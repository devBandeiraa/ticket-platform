package com.devbandeiraa.bookingservice.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.bookingservice.client.Autorizacao;
import com.devbandeiraa.bookingservice.client.EventClient;
import com.devbandeiraa.bookingservice.client.PagamentoClient;
import com.devbandeiraa.bookingservice.repository.BookingRepository;
import com.devbandeiraa.bookingservice.repository.BookingSeatRepository;
import com.devbandeiraa.bookingservice.repository.EventSeatRepository;
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
 * Agregados de venda do painel administrativo.
 *
 * <p>Os cenarios sao montados <strong>pela API</strong>, e nao gravando reservas direto no
 * repositorio. E o que torna os numeros conferiveis: a receita esperada abaixo e a soma de
 * compras que de fato passaram pelo calculo de taxa e pela confirmacao.
 */
@SpringBootTest(properties = "booking.taxa.percentual=10")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class MetricasDeVendaIntegrationTest {

    private static final BigDecimal PRECO = new BigDecimal("100.00");
    private static final int CAPACIDADE = 20;

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
    private String tokenDeUsuario;
    private String tokenDeAdmin;

    @BeforeEach
    void preparar() {
        bookingRepository.deleteAllInBatch();
        bookingSeatRepository.deleteAllInBatch();
        assentoRepository.deleteAllInBatch();

        eventoId = UUID.randomUUID();
        tokenDeUsuario = GeradorDeToken.deUsuario(UUID.randomUUID());
        tokenDeAdmin = GeradorDeToken.deAdmin();

        when(eventClient.buscarPublicado(eventoId))
                .thenReturn(PlantaDeTeste.eventoCom(eventoId, CAPACIDADE, PRECO));

        when(pagamentoClient.autorizar(any(UUID.class), any(BigDecimal.class)))
                .thenAnswer(chamada -> new Autorizacao(chamada.getArgument(0), "AUT-TESTE", false));
    }

    @Test
    @DisplayName("sem reserva alguma, devolve zeros em vez de quebrar")
    void semDadosDeveDevolverZeros() throws Exception {
        // Uma plataforma recem-instalada tem zero reservas, e a conversao dividiria por zero. Um
        // painel que quebra no primeiro acesso e pior do que um painel mostrando 0%.
        mockMvc.perform(metricas())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservasCriadas").value(0))
                .andExpect(jsonPath("$.receitaDosIngressos").value(0))
                .andExpect(jsonPath("$.conversao").value(0.0));
    }

    @Test
    @DisplayName("separa a receita dos ingressos da taxa retida pela plataforma")
    void deveSepararReceitaDeTaxa() throws Exception {
        // Duas compras de dois lugares a 100: subtotal 200 cada, taxa 20 cada.
        pagar(criarReserva(2));
        pagar(criarReserva(2));

        // Somados, os dois numeros virariam um total que nao responde nem a pergunta do
        // organizador ("quanto o evento rendeu") nem a da plataforma ("quanto eu retive").
        mockMvc.perform(metricas())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receitaDosIngressos").value(400.00))
                .andExpect(jsonPath("$.taxaArrecadada").value(40.00))
                .andExpect(jsonPath("$.ingressosVendidos").value(4));
    }

    @Test
    @DisplayName("so reserva confirmada entra na receita")
    void pendenteNaoDeveEntrarNaReceita() throws Exception {
        pagar(criarReserva(1));
        criarReserva(3);

        mockMvc.perform(metricas())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receitaDosIngressos").value(100.00))
                .andExpect(jsonPath("$.ingressosVendidos").value(1))
                .andExpect(jsonPath("$.reservasCriadas").value(2))
                .andExpect(jsonPath("$.reservasConfirmadas").value(1));
    }

    @Test
    @DisplayName("conversao e confirmadas sobre criadas, em pontos percentuais")
    void deveCalcularConversao() throws Exception {
        pagar(criarReserva(1));
        criarReserva(1);
        criarReserva(1);
        cancelar(criarReserva(1));

        // Uma confirmada em quatro criadas.
        mockMvc.perform(metricas())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservasCriadas").value(4))
                .andExpect(jsonPath("$.reservasConfirmadas").value(1))
                .andExpect(jsonPath("$.reservasCanceladas").value(1))
                .andExpect(jsonPath("$.conversao").value(25.0));
    }

    @Test
    @DisplayName("lugares disponiveis cai conforme os assentos saem do pool")
    void disponiveisDeveRefletirOPool() throws Exception {
        mockMvc.perform(metricas())
                .andExpect(jsonPath("$.lugaresDisponiveis").value(0));

        // A hidratacao so acontece na primeira reserva: antes dela, este servico nao tem assento
        // algum deste evento, e por isso o numero comeca em zero mesmo com a casa vazia.
        criarReserva(5);

        mockMvc.perform(metricas())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lugaresDisponiveis").value(CAPACIDADE - 5));
    }

    @Test
    @DisplayName("usuario comum nao ve as metricas")
    void usuarioComumDeveReceber403() throws Exception {
        mockMvc.perform(get("/admin/metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sem token, 401")
    void semTokenDeveReceber401() throws Exception {
        mockMvc.perform(get("/admin/metrics"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- auxiliares ----------

    private RequestBuilder metricas() {
        return get("/admin/metrics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAdmin);
    }

    private UUID criarReserva(int quantidade) throws Exception {
        String corpo = mockMvc.perform(post("/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"" + eventoId + "\",\"quantity\":" + quantidade + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(corpo, "$.id"));
    }

    private void pagar(UUID id) throws Exception {
        mockMvc.perform(post("/bookings/" + id + "/pay")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario))
                .andExpect(status().isOk());
    }

    private void cancelar(UUID id) throws Exception {
        mockMvc.perform(post("/bookings/" + id + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario))
                .andExpect(status().isNoContent());
    }
}
