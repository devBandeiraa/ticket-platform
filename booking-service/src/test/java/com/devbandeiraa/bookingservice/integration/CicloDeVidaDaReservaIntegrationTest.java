package com.devbandeiraa.bookingservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.bookingservice.client.EventClient;
import com.devbandeiraa.bookingservice.domain.Booking;
import com.devbandeiraa.bookingservice.domain.BookingStatus;
import com.devbandeiraa.bookingservice.domain.EventInventory;
import com.devbandeiraa.bookingservice.repository.BookingRepository;
import com.devbandeiraa.bookingservice.repository.EventInventoryRepository;
import com.devbandeiraa.bookingservice.service.ExpiracaoDeReservasJob;
import com.devbandeiraa.bookingservice.support.GeradorDeToken;
import com.devbandeiraa.bookingservice.support.TestcontainersConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Testes do que acontece com a reserva depois de criada.
 *
 * <p>As reservas sao gravadas diretamente pelo repositorio, e nao pela API. E de proposito: aqui
 * o interesse esta nas transicoes, e montar cada cenario por HTTP exigiria tambem simular o
 * estoque, a hidratacao e a idempotencia — ruido que nao ajuda a entender o que esta sendo
 * verificado. Gravando o estado inicial de forma explicita, cada teste diz em duas linhas de
 * onde parte.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class CicloDeVidaDaReservaIntegrationTest {

    private static final BigDecimal PRECO = new BigDecimal("150.00");
    private static final int CAPACIDADE = 10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventInventoryRepository estoqueRepository;

    @Autowired
    private ExpiracaoDeReservasJob jobDeExpiracao;

    @Autowired
    private TransactionTemplate transacao;

    /** Nao ha chamada ao event-service nestes testes; o estoque ja e criado hidratado. */
    @MockitoBean
    private EventClient eventClient;

    private UUID eventoId;
    private UUID usuarioId;
    private String tokenDoUsuario;

    @BeforeEach
    void limparEstado() {
        bookingRepository.deleteAllInBatch();
        estoqueRepository.deleteAllInBatch();

        eventoId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();
        tokenDoUsuario = GeradorDeToken.deUsuario(usuarioId);
        estoqueRepository.saveAndFlush(EventInventory.hidratado(eventoId, CAPACIDADE, PRECO));
    }

    // ---------- pagamento ----------

    @Test
    @DisplayName("pagar confirma a reserva e registra o instante do pagamento")
    void devePagar() throws Exception {
        Booking reserva = reservaPendente(2, prazoDe(10));

        mockMvc.perform(pagar(reserva.getId(), tokenDoUsuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BookingStatus.CONFIRMED.name()))
                .andExpect(jsonPath("$.paidAt").exists());

        // Reserva confirmada nao devolve estoque: o ingresso foi vendido.
        assertThat(reservado()).isEqualTo(2);
    }

    @Test
    @DisplayName("pagar duas vezes nao e erro: a segunda devolve a mesma reserva confirmada")
    void pagarDeveSerIdempotente() throws Exception {
        Booking reserva = reservaPendente(1, prazoDe(10));

        mockMvc.perform(pagar(reserva.getId(), tokenDoUsuario)).andExpect(status().isOk());

        // Um duplo clique no botao de pagar nao deve produzir uma tela de erro para uma
        // operacao que deu certo.
        mockMvc.perform(pagar(reserva.getId(), tokenDoUsuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BookingStatus.CONFIRMED.name()));
    }

    @Test
    @DisplayName("pagar depois do prazo devolve 409 BOOKING_EXPIRED, mesmo antes de o job passar")
    void naoDevePagarVencida() throws Exception {
        // Prazo ja vencido e status ainda PENDING: exatamente a janela entre o vencimento e a
        // proxima varredura. A protecao vem do expires_at > now() no WHERE da confirmacao, e nao
        // do job — que serve para liberar estoque, nao para impedir o pagamento.
        Booking reserva = reservaPendente(1, prazoDe(-1));

        mockMvc.perform(pagar(reserva.getId(), tokenDoUsuario))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_EXPIRED"));

        assertThat(recarregar(reserva).getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    @DisplayName("pagar reserva cancelada devolve 409 BOOKING_CANCELLED")
    void naoDevePagarCancelada() throws Exception {
        Booking reserva = reservaPendente(1, prazoDe(10));
        mockMvc.perform(cancelar(reserva.getId(), tokenDoUsuario)).andExpect(status().isNoContent());

        mockMvc.perform(pagar(reserva.getId(), tokenDoUsuario))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_CANCELLED"));
    }

    @Test
    @DisplayName("nao se paga a reserva de outra pessoa, e ela sequer aparece como existente")
    void naoDevePagarReservaAlheia() throws Exception {
        Booking reserva = reservaPendente(1, prazoDe(10));

        mockMvc.perform(pagar(reserva.getId(), GeradorDeToken.deUsuarioComum()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));

        assertThat(recarregar(reserva).getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    // ---------- cancelamento ----------

    @Test
    @DisplayName("cancelar devolve o estoque sem apagar a reserva")
    void deveCancelarDevolvendoEstoque() throws Exception {
        Booking reserva = reservaPendente(3, prazoDe(10));
        assertThat(reservado()).isEqualTo(3);

        mockMvc.perform(cancelar(reserva.getId(), tokenDoUsuario))
                .andExpect(status().isNoContent());

        assertThat(reservado()).isZero();
        // Exclusao logica: o historico do usuario precisa continuar mostrando o que houve.
        assertThat(recarregar(reserva).getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelar duas vezes devolve o estoque uma vez so")
    void cancelarDeveSerIdempotente() throws Exception {
        Booking reserva = reservaPendente(3, prazoDe(10));

        mockMvc.perform(cancelar(reserva.getId(), tokenDoUsuario)).andExpect(status().isNoContent());
        mockMvc.perform(cancelar(reserva.getId(), tokenDoUsuario)).andExpect(status().isNoContent());

        // Se a devolucao nao dependesse do UPDATE condicional ter afetado uma linha, o segundo
        // cancelamento devolveria 3 ingressos que nunca foram tomados.
        assertThat(reservado()).isZero();
    }

    @Test
    @DisplayName("cancelar reserva ja paga exige estorno, e por isso e recusado")
    void naoDeveCancelarPaga() throws Exception {
        Booking reserva = reservaPendente(2, prazoDe(10));
        mockMvc.perform(pagar(reserva.getId(), tokenDoUsuario)).andExpect(status().isOk());

        mockMvc.perform(cancelar(reserva.getId(), tokenDoUsuario))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_ALREADY_CONFIRMED"));

        assertThat(reservado()).isEqualTo(2);
    }

    // ---------- expiracao ----------

    @Test
    @DisplayName("a varredura expira reservas vencidas e devolve o estoque")
    void deveExpirarVencidas() {
        Booking vencida = reservaPendente(4, prazoDe(-5));

        jobDeExpiracao.expirarVencidas();

        assertThat(recarregar(vencida).getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(reservado()).isZero();
    }

    @Test
    @DisplayName("a varredura nao toca em reservas dentro do prazo")
    void naoDeveExpirarDentroDoPrazo() {
        Booking noPrazo = reservaPendente(2, prazoDe(30));

        jobDeExpiracao.expirarVencidas();

        assertThat(recarregar(noPrazo).getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(reservado()).isEqualTo(2);
    }

    @Test
    @DisplayName("a varredura nao mexe em reserva ja paga, mesmo com o prazo vencido")
    void naoDeveExpirarPaga() throws Exception {
        Booking reserva = reservaPendente(2, prazoDe(10));
        mockMvc.perform(pagar(reserva.getId(), tokenDoUsuario)).andExpect(status().isOk());

        jobDeExpiracao.expirarVencidas();

        assertThat(recarregar(reserva).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reservado()).isEqualTo(2);
    }

    @Test
    @DisplayName("rodar a varredura duas vezes devolve o estoque uma vez so")
    void varreduraDeveSerIdempotente() {
        reservaPendente(4, prazoDe(-5));

        // Simula o que acontece com varias replicas do servico: todas varrem, e o UPDATE
        // condicional garante que apenas uma consiga a transicao de cada reserva.
        jobDeExpiracao.expirarVencidas();
        jobDeExpiracao.expirarVencidas();

        assertThat(reservado()).isZero();
    }

    @Test
    @DisplayName("o estoque devolvido pela expiracao volta a ficar disponivel para reserva")
    void estoqueDevolvidoDeveVoltarAoMercado() {
        reservaPendente(CAPACIDADE, prazoDe(-5));
        assertThat(estoqueRepository.findById(eventoId).orElseThrow().getDisponivel()).isZero();

        jobDeExpiracao.expirarVencidas();

        // Esta e a razao de o job existir: sem ele, um carrinho abandonado esgotaria o evento
        // com assentos vazios.
        assertThat(estoqueRepository.findById(eventoId).orElseThrow().getDisponivel())
                .isEqualTo(CAPACIDADE);
    }

    // ---------- apoio ----------

    /**
     * Grava uma reserva pendente ja segurando o estoque, no mesmo par de operacoes que a API faz.
     *
     * <p>Roda dentro de um {@code TransactionTemplate} por duas razoes: o incremento do estoque e
     * uma consulta {@code @Modifying}, que exige transacao ativa, e o resultado precisa ser
     * <em>commitado</em> — as requisicoes do MockMvc rodam em transacoes proprias e nao
     * enxergariam dados ainda nao confirmados.
     */
    private Booking reservaPendente(int quantidade, Instant expiraEm) {
        return transacao.execute(status -> {
            estoqueRepository.reservar(eventoId, quantidade);
            return bookingRepository.saveAndFlush(Booking.pendente(
                    eventoId, usuarioId, quantidade, PRECO, expiraEm,
                    "chave-" + UUID.randomUUID()));
        });
    }

    private Instant prazoDe(int minutos) {
        return Instant.now().plus(minutos, ChronoUnit.MINUTES);
    }

    private Booking recarregar(Booking reserva) {
        return bookingRepository.findById(reserva.getId()).orElseThrow();
    }

    private int reservado() {
        return estoqueRepository.findById(eventoId).orElseThrow().getReservedTickets();
    }

    private org.springframework.test.web.servlet.RequestBuilder pagar(UUID id, String token) {
        return post("/bookings/" + id + "/pay")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private org.springframework.test.web.servlet.RequestBuilder cancelar(UUID id, String token) {
        return post("/bookings/" + id + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
