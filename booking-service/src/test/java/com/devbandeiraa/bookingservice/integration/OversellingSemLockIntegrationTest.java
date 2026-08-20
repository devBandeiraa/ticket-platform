package com.devbandeiraa.bookingservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.devbandeiraa.bookingservice.client.EventClient;
import com.devbandeiraa.bookingservice.client.EventSnapshot;
import com.devbandeiraa.bookingservice.dto.request.CreateBookingRequest;
import com.devbandeiraa.bookingservice.exception.EstoqueEsgotadoException;
import com.devbandeiraa.bookingservice.lock.DistributedLock;
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
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * A mesma disputa do teste anterior, porem <strong>sem lock nenhum</strong>.
 *
 * <p>Este e o teste que sustenta a tese do projeto. O lock distribuido e apresentado o tempo todo
 * como otimizacao, e nao como a garantia; uma afirmacao dessas so vale se for demonstrada. Aqui o
 * lock e substituido por uma implementacao que simplesmente executa a operacao, reproduzindo
 * exatamente o que acontece quando o Redis esta fora do ar e o servico degrada.
 *
 * <p>Sem lock, todas as 200 threads chegam ao banco ao mesmo tempo, sem serializacao previa
 * alguma. Ainda assim vendem-se exatamente 50 ingressos, porque a condicao que impede o
 * overselling — {@code reserved + quantidade <= total} — esta dentro do {@code WHERE} da mesma
 * instrucao que faz o incremento, e o PostgreSQL a avalia sob o lock de linha que ele proprio
 * adquire para atualizar.
 *
 * <p>Se este teste falhar, o projeto inteiro esta apoiado numa premissa falsa.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class OversellingSemLockIntegrationTest {

    private static final BigDecimal PRECO = new BigDecimal("100.00");
    private static final int CAPACIDADE = 50;
    private static final int COMPRADORES = 200;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventInventoryRepository estoqueRepository;

    @MockitoBean
    private EventClient eventClient;

    /** Substitui o lock do Redis por um que nao trava nada. */
    @MockitoBean
    private DistributedLock lock;

    private UUID eventoId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepararEventoSemLock() {
        bookingRepository.deleteAllInBatch();
        estoqueRepository.deleteAllInBatch();

        eventoId = UUID.randomUUID();
        when(eventClient.buscarPublicado(eventoId))
                .thenReturn(new EventSnapshot(eventoId, CAPACIDADE, PRECO));

        // Executa a operacao direto, sem adquirir coisa alguma. E o modo degradado: o servico
        // seguiu funcionando porque o Redis nao e a garantia, apenas a otimizacao.
        when(lock.executarComLock(anyString(), any()))
                .thenAnswer(chamada -> ((Supplier<Object>) chamada.getArgument(1)).get());
    }

    @RepeatedTest(3)
    @DisplayName("sem lock, 200 compradores simultaneos ainda compram exatamente 50 ingressos")
    void bancoSozinhoDeveImpedirOverselling() throws Exception {
        int sucessos = dispararCompradoresSimultaneos();

        // Nem um ingresso a mais: o UPDATE condicional recusou todo mundo que nao coube.
        assertThat(sucessos).isEqualTo(CAPACIDADE);
        assertThat(reservado()).isEqualTo(CAPACIDADE);

        // Nem um a menos: nenhuma reserva legitima foi perdida por causa da disputa.
        assertThat(bookingRepository.count()).isEqualTo(CAPACIDADE);
    }

    private int dispararCompradoresSimultaneos() throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(32);

        try {
            List<Future<Boolean>> tentativas = new ArrayList<>();
            for (int comprador = 0; comprador < COMPRADORES; comprador++) {
                String chave = "comprador-" + comprador;
                tentativas.add(executor.submit(() -> {
                    largada.await();
                    try {
                        bookingService.criar(
                                new CreateBookingRequest(eventoId, 1), UUID.randomUUID(), chave);
                        return true;
                    } catch (EstoqueEsgotadoException acabou) {
                        // Sem lock nao ha LOCK_TIMEOUT: cada thread recebe uma resposta
                        // definitiva do banco, e nao um "tente de novo".
                        return false;
                    }
                }));
            }

            largada.countDown();

            int sucessos = 0;
            for (Future<Boolean> tentativa : tentativas) {
                if (tentativa.get(60, TimeUnit.SECONDS)) {
                    sucessos++;
                }
            }

            return sucessos;
        } finally {
            executor.shutdownNow();
        }
    }

    private int reservado() {
        return estoqueRepository.findById(eventoId).orElseThrow().getReservedTickets();
    }
}
