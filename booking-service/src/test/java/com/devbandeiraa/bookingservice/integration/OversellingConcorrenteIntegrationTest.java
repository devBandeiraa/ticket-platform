package com.devbandeiraa.bookingservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.devbandeiraa.bookingservice.client.EventClient;
import com.devbandeiraa.bookingservice.client.EventSnapshot;
import com.devbandeiraa.bookingservice.domain.BookingStatus;
import com.devbandeiraa.bookingservice.dto.request.CreateBookingRequest;
import com.devbandeiraa.bookingservice.exception.EstoqueEsgotadoException;
import com.devbandeiraa.bookingservice.lock.LockIndisponivelException;
import com.devbandeiraa.bookingservice.repository.BookingRepository;
import com.devbandeiraa.bookingservice.repository.EventInventoryRepository;
import com.devbandeiraa.bookingservice.service.BookingService;
import com.devbandeiraa.bookingservice.support.TestcontainersConfig;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    private EventInventoryRepository estoqueRepository;

    @MockitoBean
    private EventClient eventClient;

    private UUID eventoId;

    @BeforeEach
    void prepararEvento() {
        bookingRepository.deleteAllInBatch();
        estoqueRepository.deleteAllInBatch();

        eventoId = UUID.randomUUID();
        when(eventClient.buscarPublicado(eventoId))
                .thenReturn(new EventSnapshot(eventoId, CAPACIDADE, PRECO));
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
        // que pegaria uma reserva gravada com quantidade divergente do estoque tomado.
        assertThat(quantidadeTotalReservada()).isEqualTo(CAPACIDADE);
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
                bookingService.criar(new CreateBookingRequest(eventoId, 1), usuarioId, chave);
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

    private int reservado() {
        return estoqueRepository.findById(eventoId).orElseThrow().getReservedTickets();
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
