package com.devbandeiraa.bookingservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.devbandeiraa.bookingservice.client.EventClient;
import com.devbandeiraa.bookingservice.domain.BookingSeat;
import com.devbandeiraa.bookingservice.domain.BookingStatus;
import com.devbandeiraa.bookingservice.dto.request.CreateBookingRequest;
import com.devbandeiraa.bookingservice.exception.AssentosIndisponiveisException;
import com.devbandeiraa.bookingservice.exception.EstoqueEsgotadoException;
import com.devbandeiraa.bookingservice.lock.LockIndisponivelException;
import com.devbandeiraa.bookingservice.repository.BookingRepository;
import com.devbandeiraa.bookingservice.repository.BookingSeatRepository;
import com.devbandeiraa.bookingservice.repository.EventSeatRepository;
import com.devbandeiraa.bookingservice.service.BookingService;
import com.devbandeiraa.bookingservice.service.EstoqueService;
import com.devbandeiraa.bookingservice.support.PlantaDeTeste;
import com.devbandeiraa.bookingservice.support.TestcontainersConfig;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * O teste central do projeto.
 *
 * <p>Muitas threads disputam simultaneamente um estoque pequeno. O que se verifica nao e que o
 * codigo "parece" correto, e sim que o numero de ingressos vendidos bate exatamente com o
 * estoque — nem um a mais, nem um a menos.
 *
 * <p>Postgres e Redis reais, e nao substitutos. Um banco em memoria com semantica de isolamento
 * diferente poderia passar aqui e vender ingresso a mais em producao, que e precisamente o
 * defeito que este teste existe para impedir.
 *
 * <p>As reservas sao feitas chamando o servico direto, sem passar por HTTP. A disputa que
 * interessa acontece entre o lock e o banco; acrescentar a camada web so somaria latencia e
 * ruido a cada thread.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class OversellingConcorrenteIntegrationTest {

    private static final BigDecimal PRECO = new BigDecimal("100.00");

    /** Pequeno de proposito: quanto menor o estoque, mais acirrada a disputa por cada unidade. */
    private static final int CAPACIDADE = 50;

    /** Quatro vezes mais gente do que ingresso, como na abertura de venda de um show concorrido. */
    private static final int COMPRADORES = 200;

    /**
     * Um cliente real repete o pedido ao receber 409 LOCK_TIMEOUT, que significa "tente de novo",
     * e nao "acabou". Sem essa repeticao, o teste mediria o quanto o lock recusa sob disputa, e
     * nao se o estoque foi vendido por inteiro.
     */
    private static final int TENTATIVAS_POR_COMPRADOR = 40;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventSeatRepository assentoRepository;

    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    @Autowired
    private EstoqueService estoqueService;

    @MockitoBean
    private EventClient eventClient;

    private UUID eventoId;

    @BeforeEach
    void prepararEvento() {
        bookingSeatRepository.deleteAllInBatch();
        bookingRepository.deleteAllInBatch();
        assentoRepository.deleteAllInBatch();

        eventoId = UUID.randomUUID();
        when(eventClient.buscarPublicado(eventoId))
                .thenReturn(PlantaDeTeste.eventoCom(eventoId, CAPACIDADE, PRECO));

        // Hidrata antes da disputa. Deixar para a primeira reserva faria as threads
        // competirem tambem pela criacao da casa, e o que se quer medir aqui e a disputa pelos
        // lugares — alem de que os testes de escolha explicita precisam dos ids de antemao.
        estoqueService.garantirHidratado(eventoId);
    }

    /**
     * Repetido porque corrida que so falha as vezes e o pior tipo de defeito: uma unica execucao
     * verde nao distingue "esta correto" de "deu sorte no escalonamento desta vez".
     */
    @RepeatedTest(3)
    @DisplayName("200 compradores simultaneos, 50 ingressos: vende exatamente 50")
    void naoDeveVenderMaisQueOEstoque() throws Exception {
        Resultado resultado = dispararCompradoresSimultaneos();

        // O numero de reservas criadas bate com o estoque: nem overselling, nem ingresso preso.
        assertThat(resultado.sucessos()).isEqualTo(CAPACIDADE);
        assertThat(resultado.esgotados()).isEqualTo(COMPRADORES - CAPACIDADE);

        assertThat(reservado()).isEqualTo(CAPACIDADE);
        assertThat(bookingRepository.count()).isEqualTo(CAPACIDADE);

        // Soma das quantidades das reservas, e nao apenas a contagem de linhas: e a comparacao
        // que pegaria uma reserva gravada com quantidade divergente dos lugares tomados.
        assertThat(quantidadeTotalReservada()).isEqualTo(CAPACIDADE);

        nenhumLugarVendidoDuasVezes();
    }

    @Test
    @DisplayName("nenhuma reserva fica gravada sem estoque correspondente")
    void naoDeveDeixarReservaSemLastro() throws Exception {
        dispararCompradoresSimultaneos();

        // Se alguma transacao tivesse gravado a reserva sem tomar o estoque, ou tomado o estoque
        // sem gravar a reserva, estes dois numeros divergiriam. Sao a mesma transacao justamente
        // para que nao possam divergir.
        assertThat(quantidadeTotalReservada()).isEqualTo(reservado());
        assertThat(bookingRepository.findAll())
                .allMatch(reserva -> reserva.getStatus() == BookingStatus.PENDING);

        // Cada reserva tem exatamente os lugares que diz ter.
        assertThat(bookingSeatRepository.count()).isEqualTo(quantidadeTotalReservada());
        nenhumLugarVendidoDuasVezes();
    }

    /**
     * A disputa que so passou a existir com assentos.
     *
     * <p>Com contador, "vender demais" era um numero passar do teto. Agora e o MESMO lugar sair
     * para duas pessoas — e e esse o caso que precisa ser impossivel. Duzentas threads pedem,
     * todas, exatamente o assento A1.
     *
     * <p>Note que aqui nao ha "melhor disponivel" a acomodar ninguem: 199 pessoas
     * necessariamente saem sem nada, e e o desfecho correto.
     */
    @RepeatedTest(3)
    @DisplayName("200 threads pedindo O MESMO lugar: exatamente uma leva")
    void doisNaoPodemLevarOMesmoLugar() throws Exception {
        UUID disputado = assentoRepository
                .findByEventIdOrderBySectorNameAscRowLabelAscSeatNumberAsc(eventoId)
                .get(0).getId();

        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger vencedores = new AtomicInteger();
        AtomicInteger recusados = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(32);

        try {
            List<Future<?>> tentativas = new ArrayList<>();
            for (int comprador = 0; comprador < COMPRADORES; comprador++) {
                String chave = "disputa-" + comprador;
                tentativas.add(executor.submit(() -> {
                    largada.await();
                    try {
                        bookingService.criar(
                                new CreateBookingRequest(eventoId, List.of(disputado), 1),
                                UUID.randomUUID(), chave);
                        vencedores.incrementAndGet();
                    } catch (AssentosIndisponiveisException jaFoi) {
                        recusados.incrementAndGet();
                    } catch (LockIndisponivelException tenteDeNovo) {
                        // O lock recusou a vez. Nao conta como vitoria nem como derrota do
                        // assento: o comprador nem chegou a disputar.
                    }
                    return null;
                }));
            }

            largada.countDown();
            for (Future<?> tentativa : tentativas) {
                tentativa.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(vencedores.get()).isEqualTo(1);
        assertThat(recusados.get()).isPositive();

        // O lugar disputado pertence a exatamente uma reserva, e o resto da casa segue livre.
        assertThat(bookingSeatRepository.findAll()).hasSize(1);
        assertThat(reservado()).isEqualTo(1);
    }

    /**
     * Tudo ou nada, sob concorrencia.
     *
     * <p>Cada thread pede um par de lugares que se sobrepoe ao do vizinho. Uma reserva que
     * conseguisse um dos dois e gravasse assim mesmo deixaria alguem com metade do que pediu —
     * e o total cobrado nao corresponderia aos lugares entregues.
     */
    @Test
    @DisplayName("conjuntos sobrepostos: ninguem fica com reserva pela metade")
    void naoDeveEntregarReservaParcial() throws Exception {
        List<UUID> casa = assentoRepository
                .findByEventIdOrderBySectorNameAscRowLabelAscSeatNumberAsc(eventoId)
                .stream().map(a -> a.getId()).toList();

        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(16);

        try {
            List<Future<?>> tentativas = new ArrayList<>();
            // Pares vizinhos: (0,1), (1,2), (2,3)... cada um disputa um lugar com o anterior.
            for (int i = 0; i < CAPACIDADE - 1; i++) {
                List<UUID> par = List.of(casa.get(i), casa.get(i + 1));
                String chave = "par-" + i;
                tentativas.add(executor.submit(() -> {
                    largada.await();
                    try {
                        bookingService.criar(new CreateBookingRequest(eventoId, par, 2),
                                UUID.randomUUID(), chave);
                    } catch (AssentosIndisponiveisException | LockIndisponivelException recusado) {
                        // Esperado: o vizinho levou um dos dois.
                    }
                    return null;
                }));
            }

            largada.countDown();
            for (Future<?> tentativa : tentativas) {
                tentativa.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // Toda reserva gravada tem exatamente os dois lugares que pediu.
        assertThat(bookingRepository.findAll())
                .allSatisfy(reserva -> assertThat(bookingSeatRepository
                        .findByIdBookingIdOrderBySectorNameAscRowLabelAscSeatNumberAsc(
                                reserva.getId()))
                        .as("reserva %s ficou pela metade", reserva.getId())
                        .hasSize(2));

        nenhumLugarVendidoDuasVezes();
    }

    private Resultado dispararCompradoresSimultaneos() throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger esgotados = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(32);

        try {
            List<Future<Boolean>> tentativas = new ArrayList<>();
            for (int comprador = 0; comprador < COMPRADORES; comprador++) {
                String chave = "comprador-" + comprador;
                tentativas.add(executor.submit(() -> {
                    // Todas as threads ficam presas aqui e sao soltas de uma vez. Sem isso, as
                    // primeiras terminariam antes de as ultimas comecarem, e nao haveria disputa.
                    largada.await();
                    return tentarComprar(chave, esgotados);
                }));
            }

            largada.countDown();

            int sucessos = 0;
            for (Future<Boolean> tentativa : tentativas) {
                if (tentativa.get(60, TimeUnit.SECONDS)) {
                    sucessos++;
                }
            }

            return new Resultado(sucessos, esgotados.get());
        } finally {
            executor.shutdownNow();
        }
    }

    /** Insiste enquanto a resposta for "tente de novo", desiste quando for "acabou". */
    private boolean tentarComprar(String chave, AtomicInteger esgotados) {
        UUID usuarioId = UUID.randomUUID();

        for (int tentativa = 0; tentativa < TENTATIVAS_POR_COMPRADOR; tentativa++) {
            try {
                bookingService.criar(new CreateBookingRequest(eventoId, null, 1), usuarioId, chave);
                return true;
            } catch (EstoqueEsgotadoException acabou) {
                esgotados.incrementAndGet();
                return false;
            } catch (LockIndisponivelException tenteDeNovo) {
                // Evento sob disputa neste instante. A proxima tentativa tende a passar.
            }
        }

        throw new AssertionError(
                "comprador desistiu apos %d tentativas: o lock esta recusando demais"
                        .formatted(TENTATIVAS_POR_COMPRADOR));
    }

    /**
     * Quantos lugares sairam de FREE.
     *
     * <p>Contado sobre os proprios assentos, e nao lido de um contador: e a diferenca que a Fase
     * 17 introduziu, e o que torna impossivel o numero discordar do que foi vendido.
     */
    private int reservado() {
        return (int) (assentoRepository.countByEventId(eventoId)
                - assentoRepository.contarLivres(eventoId));
    }

    /**
     * Nenhum lugar pertence a mais de uma reserva.
     *
     * <p>A verificacao que so faz sentido depois dos assentos existirem. Com contador, "vendeu
     * demais" era um numero maior que o teto; agora e o MESMO lugar aparecendo em duas reservas,
     * e e isso que precisa ser impossivel.
     */
    private void nenhumLugarVendidoDuasVezes() {
        Map<UUID, Long> porAssento = bookingSeatRepository.findAll().stream()
                .collect(Collectors.groupingBy(BookingSeat::getSeatId, Collectors.counting()));

        assertThat(porAssento.values())
                .as("algum lugar foi atribuido a mais de uma reserva")
                .allMatch(quantas -> quantas == 1L);
    }

    private int quantidadeTotalReservada() {
        return bookingRepository.findAll().stream()
                .filter(reserva -> reserva.getStatus() == BookingStatus.PENDING)
                .mapToInt(reserva -> reserva.getQuantity())
                .sum();
    }

    private record Resultado(int sucessos, int esgotados) {
    }
}
