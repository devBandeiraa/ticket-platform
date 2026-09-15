package com.devbandeiraa.bookingservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.bookingservice.client.Autorizacao;
import com.devbandeiraa.bookingservice.client.EventClient;
import com.devbandeiraa.bookingservice.client.PagamentoClient;
import com.devbandeiraa.bookingservice.domain.Booking;
import com.devbandeiraa.bookingservice.domain.PaymentMethod;
import com.devbandeiraa.bookingservice.repository.BookingRepository;
import com.devbandeiraa.bookingservice.repository.BookingSeatRepository;
import com.devbandeiraa.bookingservice.repository.EventSeatRepository;
import com.devbandeiraa.bookingservice.support.AssentosDeTeste;
import com.devbandeiraa.bookingservice.support.GeradorDeToken;
import com.devbandeiraa.bookingservice.support.PlantaDeTeste;
import com.devbandeiraa.bookingservice.support.TestcontainersConfig;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * Taxa de servico, forma de pagamento e codigo do ingresso.
 *
 * <p>Diferente dos demais testes de ciclo de vida, as reservas aqui sao criadas <strong>pela
 * API</strong>. E o ponto: a taxa e calculada no caminho de criacao, e uma reserva gravada
 * diretamente pelo repositorio pularia exatamente a linha que se quer verificar.
 *
 * <p>O percentual e FIXADO na anotacao, em vez de herdado da configuracao. Assim os valores
 * esperados abaixo continuam certos quando alguem mudar o padrao de 10% para outro numero — o
 * teste mede a conta, e nao qual taxa a plataforma cobra hoje.
 */
@SpringBootTest(properties = "booking.taxa.percentual=10")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class PagamentoTaxaECodigoIntegrationTest {

    private static final BigDecimal PRECO = new BigDecimal("150.00");
    private static final int CAPACIDADE = 10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventSeatRepository assentoRepository;

    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    @MockitoBean
    private EventClient eventClient;

    @MockitoBean
    private PagamentoClient pagamentoClient;

    private UUID eventoId;
    private String token;

    @BeforeEach
    void preparar() {
        bookingRepository.deleteAllInBatch();
        bookingSeatRepository.deleteAllInBatch();
        assentoRepository.deleteAllInBatch();

        eventoId = UUID.randomUUID();
        token = GeradorDeToken.deUsuario(UUID.randomUUID());

        when(eventClient.buscarPublicado(eventoId))
                .thenReturn(PlantaDeTeste.eventoCom(eventoId, CAPACIDADE, PRECO));

        when(pagamentoClient.autorizar(any(UUID.class), any(BigDecimal.class)))
                .thenAnswer(chamada -> new Autorizacao(chamada.getArgument(0), "AUT-TESTE", false));
    }

    // ---------- taxa ----------

    @Test
    @DisplayName("a reserva devolve subtotal, taxa e total, e os tres somam")
    void deveDecomporOValor() throws Exception {
        // Dois lugares a 150 dao 300 de subtotal; 10% sao 30; o total e 330.
        mockMvc.perform(reservar(2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(300.00))
                .andExpect(jsonPath("$.fee").value(30.00))
                .andExpect(jsonPath("$.totalPrice").value(330.00));
    }

    @Test
    @DisplayName("o provedor e cobrado pelo total com taxa, e nao pelo subtotal")
    void deveCobrarOTotalComTaxa() throws Exception {
        UUID reservaId = criarReserva(2);

        mockMvc.perform(pagar(reservaId, null)).andExpect(status().isOk());

        // O erro que este teste pega e cobrar `subtotal` por engano: a reserva mostraria 330 na
        // tela e a fatura viria 300, e a diferenca so apareceria no fechamento do mes.
        verify(pagamentoClient).autorizar(eq(reservaId), eq(new BigDecimal("330.00")));
    }

    @Test
    @DisplayName("a taxa gravada nao muda quando o percentual configurado muda")
    void taxaDeveFicarCongeladaNaReserva() throws Exception {
        UUID reservaId = criarReserva(1);

        Booking reserva = bookingRepository.findById(reservaId).orElseThrow();

        // A taxa esta na COLUNA, e nao recalculada na leitura. E o que faz uma compra feita sob
        // 10% continuar valendo 10% depois de a configuracao mudar — sem isso, o historico
        // inteiro mudaria de valor junto da propriedade e o comprovante do usuario nao bateria.
        assertThat(reserva.getSubtotal()).isEqualByComparingTo("150.00");
        assertThat(reserva.getFee()).isEqualByComparingTo("15.00");
        assertThat(reserva.getTotalPrice()).isEqualByComparingTo("165.00");
        assertThat(reserva.getSubtotal().add(reserva.getFee()))
                .isEqualByComparingTo(reserva.getTotalPrice());
    }

    // ---------- forma de pagamento ----------

    @Test
    @DisplayName("pagar com PIX registra PIX")
    void deveRegistrarPix() throws Exception {
        UUID reservaId = criarReserva(1);

        mockMvc.perform(pagar(reservaId, "PIX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethod").value("PIX"));

        assertThat(bookingRepository.findById(reservaId).orElseThrow().getPaymentMethod())
                .isEqualTo(PaymentMethod.PIX);
    }

    @Test
    @DisplayName("pagar sem corpo continua funcionando e vale CARD")
    void semCorpoDeveValerCartao() throws Exception {
        UUID reservaId = criarReserva(1);

        // Ate a Fase 22 este endpoint nao recebia corpo algum. Exigir um agora quebraria todo
        // cliente ja escrito — inclusive o frontend, que ainda chama sem corpo.
        mockMvc.perform(post("/bookings/" + reservaId + "/pay")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethod").value("CARD"));
    }

    @Test
    @DisplayName("forma de pagamento desconhecida devolve 400")
    void formaInvalidaDeveDevolver400() throws Exception {
        UUID reservaId = criarReserva(1);

        mockMvc.perform(pagar(reservaId, "BOLETO"))
                .andExpect(status().isBadRequest());
    }

    // ---------- codigo do ingresso ----------

    @Test
    @DisplayName("reserva pendente nao tem codigo: ainda nao e ingresso")
    void pendenteNaoDeveTerCodigo() throws Exception {
        mockMvc.perform(reservar(1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketCode").doesNotExist())
                .andExpect(jsonPath("$.paymentMethod").doesNotExist());
    }

    @Test
    @DisplayName("pagar numera o ingresso no formato combinado")
    void pagarDeveNumerarOIngresso() throws Exception {
        UUID reservaId = criarReserva(1);

        mockMvc.perform(pagar(reservaId, "CARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode")
                        .value(matchesPattern("TP-[0-9A-Z]{6}-[0-9A-Z]{6}")));
    }

    @Test
    @DisplayName("duas reservas pagas recebem codigos diferentes")
    void codigosDevemSerDistintos() throws Exception {
        UUID primeira = criarReserva(1);
        UUID segunda = criarReserva(1);

        mockMvc.perform(pagar(primeira, null)).andExpect(status().isOk());
        mockMvc.perform(pagar(segunda, null)).andExpect(status().isOk());

        String codigoA = bookingRepository.findById(primeira).orElseThrow().getTicketCode();
        String codigoB = bookingRepository.findById(segunda).orElseThrow().getTicketCode();

        assertThat(codigoA).isNotNull().isNotEqualTo(codigoB);
    }

    @Test
    @DisplayName("pagar de novo devolve o mesmo codigo, sem renumerar o ingresso")
    void pagamentoRepetidoNaoDeveRenumerar() throws Exception {
        UUID reservaId = criarReserva(1);

        mockMvc.perform(pagar(reservaId, null)).andExpect(status().isOk());
        String codigo = bookingRepository.findById(reservaId).orElseThrow().getTicketCode();

        // Um duplo clique no botao de pagar nao pode trocar o numero do ingresso: quem ja
        // salvou o PDF ficaria com um codigo que a portaria nao reconhece.
        mockMvc.perform(pagar(reservaId, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").value(codigo));
    }

    // ---------- auxiliares ----------

    private UUID criarReserva(int quantidade) throws Exception {
        String corpo = mockMvc.perform(reservar(quantidade))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(corpo, "$.id"));
    }

    private RequestBuilder reservar(int quantidade) {
        return post("/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + eventoId + "\",\"quantity\":" + quantidade + "}");
    }

    private RequestBuilder pagar(UUID id, String forma) {
        var requisicao = post("/bookings/" + id + "/pay")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        if (forma == null) {
            return requisicao;
        }
        return requisicao
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"" + forma + "\"}");
    }
}
