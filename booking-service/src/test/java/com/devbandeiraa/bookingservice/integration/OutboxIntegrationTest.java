package com.devbandeiraa.bookingservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.bookingservice.client.EventClient;
import com.devbandeiraa.bookingservice.domain.Booking;
import com.devbandeiraa.bookingservice.domain.EventInventory;
import com.devbandeiraa.bookingservice.domain.OutboxMessage;
import com.devbandeiraa.bookingservice.domain.OutboxStatus;
import com.devbandeiraa.bookingservice.messaging.BookingConfirmedEvent;
import com.devbandeiraa.bookingservice.messaging.OutboxPublisher;
import com.devbandeiraa.bookingservice.repository.BookingRepository;
import com.devbandeiraa.bookingservice.repository.EventInventoryRepository;
import com.devbandeiraa.bookingservice.repository.OutboxRepository;
import com.devbandeiraa.bookingservice.support.GeradorDeToken;
import com.devbandeiraa.bookingservice.support.TestcontainersConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Testes da outbox transacional.
 *
 * <p>O broker e real, mas o {@code RabbitTemplate} e espionado para que a falha de publicacao
 * possa ser provocada sob demanda. Derrubar o container no meio do teste tambem funcionaria, e
 * levaria dezenas de segundos para reproduzir o que aqui se reproduz em milissegundos — sendo
 * que o que importa verificar e a reacao a falha, e nao a falha em si.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class OutboxIntegrationTest {

    private static final BigDecimal PRECO = new BigDecimal("150.00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventInventoryRepository estoqueRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private TransactionTemplate transacao;

    @MockitoBean
    private EventClient eventClient;

    @MockitoSpyBean
    private RabbitTemplate rabbitTemplate;

    private UUID eventoId;
    private UUID usuarioId;
    private String tokenDoUsuario;

    @BeforeEach
    void limparEstado() {
        outboxRepository.deleteAllInBatch();
        bookingRepository.deleteAllInBatch();
        estoqueRepository.deleteAllInBatch();

        eventoId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();
        tokenDoUsuario = GeradorDeToken.deUsuario(usuarioId);
        estoqueRepository.saveAndFlush(EventInventory.hidratado(eventoId, 10, PRECO));
    }

    // ---------- gravacao ----------

    @Test
    @DisplayName("pagar grava o evento na outbox, na mesma transacao da confirmacao")
    void pagarDeveGravarEventoNaOutbox() throws Exception {
        Booking reserva = reservaPendente(2);

        mockMvc.perform(pagar(reserva.getId())).andExpect(status().isOk());

        assertThat(outboxRepository.findAll()).singleElement().satisfies(mensagem -> {
            assertThat(mensagem.getType()).isEqualTo(BookingConfirmedEvent.TIPO);
            assertThat(mensagem.getAggregateId()).isEqualTo(reserva.getId());
            assertThat(mensagem.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(mensagem.getAttempts()).isZero();
            // Payload ja serializado na gravacao: o evento congela o que aconteceu naquele
            // instante, independente de como a classe evoluir depois.
            assertThat(mensagem.getPayload())
                    .contains(reserva.getId().toString())
                    .contains(usuarioId.toString())
                    .contains("300.00");
        });
    }

    @Test
    @DisplayName("pagamento recusado nao deixa evento na outbox")
    void pagamentoRecusadoNaoDeveGravarEvento() throws Exception {
        Booking vencida = reservaPendente(1, Instant.now().minus(1, ChronoUnit.MINUTES));

        mockMvc.perform(pagar(vencida.getId())).andExpect(status().isConflict());

        // Se o evento fosse gravado fora da transacao, ou antes da confirmacao, haveria aqui uma
        // notificacao de pagamento que nunca aconteceu.
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("pagar duas vezes gera um evento so")
    void pagamentoIdempotenteNaoDeveDuplicarEvento() throws Exception {
        Booking reserva = reservaPendente(1);

        mockMvc.perform(pagar(reserva.getId())).andExpect(status().isOk());
        mockMvc.perform(pagar(reserva.getId())).andExpect(status().isOk());

        // A segunda chamada nao passou pela confirmacao: o UPDATE condicional afetou zero linhas
        // e o caminho de idempotencia devolveu a reserva sem registrar nada.
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    // ---------- publicacao ----------

    @Test
    @DisplayName("o publicador entrega ao broker e marca a mensagem como publicada")
    void devePublicarEMarcar() throws Exception {
        Booking reserva = reservaPendente(1);
        mockMvc.perform(pagar(reserva.getId())).andExpect(status().isOk());

        outboxPublisher.publicarPendentes();

        assertThat(outboxRepository.findAll()).singleElement().satisfies(mensagem -> {
            assertThat(mensagem.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(mensagem.getPublishedAt()).isNotNull();
            assertThat(mensagem.getAttempts()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("varrer de novo nao republica o que ja foi publicado")
    void naoDeveRepublicar() throws Exception {
        Booking reserva = reservaPendente(1);
        mockMvc.perform(pagar(reserva.getId())).andExpect(status().isOk());

        outboxPublisher.publicarPendentes();
        outboxPublisher.publicarPendentes();

        assertThat(primeiraMensagem().getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("broker fora do ar mantem a mensagem pendente para a proxima varredura")
    void falhaNoBrokerDeveManterPendente() throws Exception {
        Booking reserva = reservaPendente(1);
        mockMvc.perform(pagar(reserva.getId())).andExpect(status().isOk());

        simularBrokerIndisponivel();
        outboxPublisher.publicarPendentes();

        // Este e o ponto da outbox: a reserva segue paga e correta, e a notificacao nao se perdeu
        // — apenas ainda nao saiu.
        OutboxMessage mensagem = primeiraMensagem();
        assertThat(mensagem.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(mensagem.getAttempts()).isEqualTo(1);
        assertThat(mensagem.getLastError()).isNotBlank();
        assertThat(mensagem.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("apos o limite de tentativas, desiste da mensagem para nao travar as demais")
    void deveDesistirAposOLimite() throws Exception {
        Booking reserva = reservaPendente(1);
        mockMvc.perform(pagar(reserva.getId())).andExpect(status().isOk());

        simularBrokerIndisponivel();
        // max-tentativas e 5; a quinta varredura deve marcar como FAILED.
        for (int varredura = 0; varredura < 5; varredura++) {
            outboxPublisher.publicarPendentes();
        }

        OutboxMessage mensagem = primeiraMensagem();
        assertThat(mensagem.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(mensagem.getAttempts()).isEqualTo(5);

        // Estado terminal: uma mensagem envenenada nao pode ocupar o lote a cada varredura e
        // atrasar todas as outras indefinidamente.
        outboxPublisher.publicarPendentes();
        assertThat(primeiraMensagem().getAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("broker inalcancavel nao consome tentativa, por mais varreduras que passem")
    void naoDeveConsumirTentativaComBrokerInalcancavel() throws Exception {
        Booking reserva = reservaPendente(1);
        mockMvc.perform(pagar(reserva.getId())).andExpect(status().isOk());

        simularBrokerInalcancavel();

        // O dobro do limite de tentativas. Se falha de transporte consumisse orcamento, a
        // mensagem ja teria sido descartada na quinta.
        for (int varredura = 0; varredura < 10; varredura++) {
            outboxPublisher.publicarPendentes();
        }

        // O orcamento de tentativas existe para mensagem defeituosa. Broker fora do ar nao diz
        // nada sobre a mensagem, e gastar tentativa nisso transformava o limite num cronometro:
        // com varredura a cada 2s, dez segundos de indisponibilidade perdiam a notificacao.
        OutboxMessage mensagem = primeiraMensagem();
        assertThat(mensagem.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(mensagem.getAttempts()).isZero();

        // E, com o broker de volta, ela sai normalmente.
        org.mockito.Mockito.reset(rabbitTemplate);
        outboxPublisher.publicarPendentes();
        assertThat(primeiraMensagem().getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    // ---------- apoio ----------

    /** Faz o proximo envio ao broker falhar, sem derrubar o container. */
    /** Broker inalcancavel: falha de transporte, e nao recusa da mensagem. */
    private void simularBrokerInalcancavel() {
        doThrow(new AmqpConnectException(new java.net.ConnectException("rabbitmq: nao resolve")))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class),
                        any(MessagePostProcessor.class));
    }

    private void simularBrokerIndisponivel() {
        doThrow(new AmqpException("broker indisponivel"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class),
                        any(MessagePostProcessor.class));
    }

    private OutboxMessage primeiraMensagem() {
        return outboxRepository.findAll().stream().findFirst().orElseThrow();
    }

    private Booking reservaPendente(int quantidade) {
        return reservaPendente(quantidade, Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    private Booking reservaPendente(int quantidade, Instant expiraEm) {
        return transacao.execute(status -> {
            estoqueRepository.reservar(eventoId, quantidade);
            return bookingRepository.saveAndFlush(Booking.pendente(
                    eventoId, usuarioId, quantidade, PRECO, expiraEm, "chave-" + UUID.randomUUID()));
        });
    }

    private org.springframework.test.web.servlet.RequestBuilder pagar(UUID id) {
        return post("/bookings/" + id + "/pay")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDoUsuario);
    }
}
