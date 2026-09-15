package com.devbandeiraa.eventservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.eventservice.domain.EventStatus;
import com.devbandeiraa.eventservice.repository.EventRepository;
import com.devbandeiraa.eventservice.support.GeradorDeToken;
import com.devbandeiraa.eventservice.support.PostgresContainerConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Testes de integracao do CRUD administrativo e da autorizacao, contra um PostgreSQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class AdminEventCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void limparEstado() {
        eventRepository.deleteAllInBatch();
    }

    // ---------- autorizacao ----------

    @Test
    @DisplayName("sem token, a administracao devolve 401")
    void deveExigirAutenticacao() throws Exception {
        mockMvc.perform(get("/admin/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("com token de usuario comum, a administracao devolve 403")
    void deveRecusarUsuarioComum() throws Exception {
        mockMvc.perform(get("/admin/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.deUsuarioComum()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("token assinado com outra chave e recusado, mesmo dizendo ser ADMIN")
    void deveRecusarTokenForjado() throws Exception {
        // O papel dentro do token diz ADMIN, mas a assinatura nao confere com o segredo do
        // servico. Se este teste passasse a devolver 200, qualquer um seria administrador.
        mockMvc.perform(get("/admin/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.assinadoComOutraChave()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token expirado e recusado")
    void deveRecusarTokenExpirado() throws Exception {
        mockMvc.perform(get("/admin/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.expirado()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- criacao ----------

    @Test
    @DisplayName("admin cria evento, que nasce como rascunho")
    void deveCriarEventoComoRascunho() throws Exception {
        mockMvc.perform(criar(corpoValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Show de Rock"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalTickets").value(500));
    }

    @Test
    @DisplayName("o autor registrado e o dono do token, ignorando qualquer id enviado no corpo")
    void deveRegistrarAutorDoToken() throws Exception {
        UUID idDoAdmin = UUID.randomUUID();

        Map<String, Object> corpoComAutorFalsificado = corpoValido();
        corpoComAutorFalsificado.put("createdBy", UUID.randomUUID().toString());

        mockMvc.perform(post("/admin/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.deAdmin(idDoAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpoComAutorFalsificado)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value(idDoAdmin.toString()));
    }

    @Test
    @DisplayName("recusa data no passado")
    void deveRecusarDataNoPassado() throws Exception {
        Map<String, Object> corpo = corpoValido();
        corpo.put("eventDate", Instant.now().minus(1, ChronoUnit.DAYS).toString());

        mockMvc.perform(criar(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.eventDate").isNotEmpty());
    }

    @Test
    @DisplayName("recusa setor sem nenhuma fila")
    void deveRecusarSetorVazio() throws Exception {
        Map<String, Object> corpo = corpoValido();
        corpo.put("sectors", List.of(setor("Plateia", "150.00", 0, 20)));

        mockMvc.perform(criar(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['sectors[0].rowsCount']").isNotEmpty());
    }

    @Test
    @DisplayName("recusa evento sem setor algum")
    void deveRecusarEventoSemSetores() throws Exception {
        Map<String, Object> corpo = corpoValido();
        corpo.put("sectors", List.of());

        // Sem setor nao ha de onde derivar capacidade nem preco, e o evento seria uma casa
        // sem lugar nenhum.
        mockMvc.perform(criar(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.sectors").isNotEmpty());
    }

    @Test
    @DisplayName("recusa preco negativo no setor")
    void deveRecusarPrecoNegativo() throws Exception {
        Map<String, Object> corpo = corpoValido();
        corpo.put("sectors", List.of(setor("Plateia", "-1.00", 25, 20)));

        mockMvc.perform(criar(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['sectors[0].price']").isNotEmpty());
    }

    @Test
    @DisplayName("capacidade e preco sao derivados dos setores, e nao aceitos do cliente")
    void deveDerivarCapacidadeEPrecoDosSetores() throws Exception {
        Map<String, Object> corpo = corpoValido();
        corpo.put("sectors", List.of(
                setor("Plateia", "180.00", 40, 25),   // 1000
                setor("Frisas", "240.00", 10, 15),    //  150
                setor("Galeria", "70.00", 14, 25)));  //  350
        // Enviados a esmo: o servidor deve ignora-los e calcular a partir dos setores. Aceitos,
        // o catalogo anunciaria uma casa que nao existe.
        corpo.put("totalTickets", 999999);
        corpo.put("price", new BigDecimal("1.00"));

        mockMvc.perform(criar(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalTickets").value(1500))
                // O menor preco entre os setores: o "a partir de" do cartao.
                .andExpect(jsonPath("$.price").value(70.00))
                .andExpect(jsonPath("$.sectors.length()").value(3))
                // A ordem de exibicao vem da ordem em que os setores foram declarados.
                .andExpect(jsonPath("$.sectors[0].name").value("Plateia"))
                .andExpect(jsonPath("$.sectors[2].name").value("Galeria"))
                .andExpect(jsonPath("$.sectors[1].capacity").value(150))
                // Os rotulos das filas vem prontos do servidor, para as duas pontas nao
                // chegarem a nomes diferentes para a mesma fila.
                .andExpect(jsonPath("$.sectors[1].rowLabels[0]").value("A"))
                .andExpect(jsonPath("$.sectors[1].rowLabels[9]").value("J"));
    }

    // ---------- ciclo de vida ----------

    @Test
    @DisplayName("publicar torna o evento visivel no catalogo publico")
    void devePublicarEvento() throws Exception {
        String id = criarEObterId();

        mockMvc.perform(post("/admin/events/" + id + "/publish").header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get("/events/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    @DisplayName("alterar atualiza os dados do evento")
    void deveAlterarEvento() throws Exception {
        String id = criarEObterId();

        Map<String, Object> alteracao = corpoValido();
        alteracao.put("name", "Show de Jazz");
        // O preco do evento nao e mais um campo proprio: muda-se o setor, e o evento passa a
        // anunciar o menor preco entre eles. O evento ainda e rascunho, entao o layout pode
        // mudar.
        alteracao.put("sectors", List.of(setor("Plateia", "99.90", 25, 20)));

        mockMvc.perform(put("/admin/events/" + id)
                        .header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alteracao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Show de Jazz"))
                .andExpect(jsonPath("$.price").value(99.90));
    }

    @Test
    @DisplayName("cancelar e exclusao logica: o registro permanece no banco")
    void deveCancelarSemApagar() throws Exception {
        String id = criarEObterId();

        mockMvc.perform(delete("/admin/events/" + id).header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin()))
                .andExpect(status().isNoContent());

        // O registro continua existindo — reservas futuras apontarao para ele.
        assertThat(eventRepository.findById(UUID.fromString(id)))
                .isPresent()
                .get()
                .satisfies(evento -> assertThat(evento.getStatus()).isEqualTo(EventStatus.CANCELLED));
    }

    @Test
    @DisplayName("evento cancelado nao pode mais ser alterado")
    void naoDeveAlterarEventoCancelado() throws Exception {
        String id = criarEObterId();
        mockMvc.perform(delete("/admin/events/" + id).header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin()));

        mockMvc.perform(put("/admin/events/" + id)
                        .header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpoValido())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EVENT_NOT_EDITABLE"));
    }

    @Test
    @DisplayName("admin enxerga rascunhos, que o catalogo publico esconde")
    void adminDeveEnxergarRascunhos() throws Exception {
        criarEObterId();

        mockMvc.perform(get("/admin/events").header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("evento inexistente devolve 404")
    void deveDevolverNaoEncontrado() throws Exception {
        mockMvc.perform(get("/admin/events/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("EVENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("id malformado na URL e erro do cliente, nao falha do servidor")
    void deveTratarIdMalformado() throws Exception {
        mockMvc.perform(get("/admin/events/nao-e-um-uuid").header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
    }

    // ---------- layout depois de publicado ----------

    @Test
    @DisplayName("publicado, o evento recusa mudanca de setor com 409")
    void naoDeveAlterarLayoutDepoisDePublicado() throws Exception {
        String id = publicarEObterId();

        Map<String, Object> alteracao = corpoValido();
        // Metade dos lugares desaparece. Se isto passasse, um assento ja vendido poderia
        // deixar de existir, e a capacidade cairia abaixo do que o booking-service ja copiou.
        alteracao.put("sectors", List.of(setor("Plateia", "150.00", 12, 20)));

        mockMvc.perform(put("/admin/events/" + id)
                        .header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alteracao)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EVENT_LAYOUT_LOCKED"));
    }

    @Test
    @DisplayName("publicado, o evento ainda aceita mudanca de nome, data e capa")
    void deveAlterarDadosDePublicadoSemMexerNoLayout() throws Exception {
        String id = publicarEObterId();

        // O mesmo record carrega dados e layout, entao a tela reenvia os setores inalterados
        // junto de qualquer edicao. Sem comparar o layout antes de recusar, esta requisicao —
        // que so muda o nome — levaria um 409 por algo que ninguem tentou mudar.
        Map<String, Object> alteracao = corpoValido();
        alteracao.put("name", "Show de Jazz");

        mockMvc.perform(put("/admin/events/" + id)
                        .header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alteracao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Show de Jazz"));
    }

    @Test
    @DisplayName("preco reenviado com outra escala nao conta como mudanca de layout")
    void escalaDoPrecoNaoDeveContarComoMudanca() throws Exception {
        String id = publicarEObterId();

        // O banco devolve 150.00; um cliente pode reenviar 150.0 ou 150. BigDecimal.equals leva
        // a escala em conta e diria que sao diferentes — e editar so o nome de um evento
        // publicado passaria a falhar. A comparacao usa compareTo justamente por isto.
        Map<String, Object> alteracao = corpoValido();
        alteracao.put("name", "Show de Jazz");
        alteracao.put("sectors", List.of(setor("Plateia", "150.0", 25, 20)));

        mockMvc.perform(put("/admin/events/" + id)
                        .header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alteracao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Show de Jazz"));
    }

    // ---------- setor como oferta comercial ----------

    @Test
    @DisplayName("descricao, beneficios e faixa do setor voltam na resposta")
    void deveGuardarSetorDescritivo() throws Exception {
        Map<String, Object> corpo = corpoValido();
        Map<String, Object> camarote = setor("Camarote", "680.00", 10, 40);
        camarote.put("description", "Cabines laterais com mesa.");
        camarote.put("benefits", List.of("Entrada exclusiva", "Servico de bar na mesa"));
        camarote.put("tier", "VIP");
        corpo.put("sectors", List.of(camarote));

        mockMvc.perform(criar(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sectors[0].description").value("Cabines laterais com mesa."))
                .andExpect(jsonPath("$.sectors[0].benefits.length()").value(2))
                .andExpect(jsonPath("$.sectors[0].benefits[0]").value("Entrada exclusiva"))
                .andExpect(jsonPath("$.sectors[0].tier").value("VIP"));
    }

    @Test
    @DisplayName("setor sem os campos novos nasce STANDARD e com lista vazia, nunca nula")
    void setorSemCamposNovosDeveTerPadrao() throws Exception {
        // A tela percorre `benefits` sem testar nulidade antes. Nulo aqui viraria um erro de
        // runtime no navegador, e a origem estaria a tres servicos de distancia.
        mockMvc.perform(criar(corpoValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sectors[0].tier").value("STANDARD"))
                .andExpect(jsonPath("$.sectors[0].benefits").isArray())
                .andExpect(jsonPath("$.sectors[0].benefits.length()").value(0))
                .andExpect(jsonPath("$.sectors[0].description").doesNotExist());
    }

    @Test
    @DisplayName("mais de seis beneficios devolve 400, e nao estoura a constraint")
    void deveRecusarBeneficiosDemais() throws Exception {
        Map<String, Object> corpo = corpoValido();
        Map<String, Object> excessivo = setor("Plateia", "150.00", 25, 20);
        excessivo.put("benefits", List.of("a", "b", "c", "d", "e", "f", "g"));
        corpo.put("sectors", List.of(excessivo));

        // O CHECK da migration recusaria do mesmo jeito, mas com 500 sobre algo que o usuario
        // digitou, e a mensagem falaria de constraint em vez de falar de beneficio.
        mockMvc.perform(criar(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['sectors[0].benefits']").isNotEmpty());
    }

    @Test
    @DisplayName("publicado, o evento ainda aceita corrigir beneficio e faixa do setor")
    void deveAlterarApresentacaoDoSetorDepoisDePublicado() throws Exception {
        String id = publicarEObterId();

        // Esta e a razao de a comparacao olhar so a planta. Beneficio e texto de vitrine: nao
        // muda o lugar de ninguem que ja comprou, e tranca-lo na publicacao faria um erro de
        // digitacao virar permanente.
        Map<String, Object> alteracao = corpoValido();
        Map<String, Object> mesmoTamanho = setor("Plateia", "150.00", 25, 20);
        mesmoTamanho.put("description", "Piso principal, de frente para o palco.");
        mesmoTamanho.put("benefits", List.of("Programa impresso"));
        mesmoTamanho.put("tier", "VIP");
        alteracao.put("sectors", List.of(mesmoTamanho));

        mockMvc.perform(put("/admin/events/" + id)
                        .header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alteracao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectors[0].benefits[0]").value("Programa impresso"))
                .andExpect(jsonPath("$.sectors[0].tier").value("VIP"));
    }

    @Test
    @DisplayName("publicado, mexer na planta junto do beneficio continua sendo 409")
    void apresentacaoNovaNaoDeveLiberarMudancaDePlanta() throws Exception {
        String id = publicarEObterId();

        // Guarda contra a brecha que a divisao poderia abrir: um beneficio novo no mesmo corpo
        // nao pode servir de carona para encolher a casa.
        Map<String, Object> alteracao = corpoValido();
        Map<String, Object> menor = setor("Plateia", "150.00", 12, 20);
        menor.put("benefits", List.of("Programa impresso"));
        alteracao.put("sectors", List.of(menor));

        mockMvc.perform(put("/admin/events/" + id)
                        .header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alteracao)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EVENT_LAYOUT_LOCKED"));
    }

    @Test
    @DisplayName("categoria ausente na criacao devolve 400")
    void deveExigirCategoria() throws Exception {
        Map<String, Object> corpo = corpoValido();
        corpo.remove("category");

        mockMvc.perform(criar(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.category").isNotEmpty());
    }

    // ---------- auxiliares ----------

    private Map<String, Object> corpoValido() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("name", "Show de Rock");
        corpo.put("description", "Uma noite inesquecivel");
        corpo.put("venue", "Estadio Municipal");
        corpo.put("eventDate", Instant.now().plus(30, ChronoUnit.DAYS).toString());
        corpo.put("category", "SHOWS");
        // 25 filas de 20 lugares = 500, a mesma capacidade que estes testes usavam quando ela
        // era um inteiro solto no corpo. Capacidade e preco nao vem mais na requisicao: sao
        // derivados dos setores pelo servidor.
        corpo.put("sectors", new ArrayList<>(List.of(setor("Plateia", "150.00", 25, 20))));
        return corpo;
    }

    private static Map<String, Object> setor(String nome, String preco, int filas, int lugares) {
        Map<String, Object> setor = new LinkedHashMap<>();
        setor.put("name", nome);
        setor.put("price", new BigDecimal(preco));
        setor.put("rowsCount", filas);
        setor.put("seatsPerRow", lugares);
        return setor;
    }

    private MockHttpServletRequestBuilder criar(Map<String, Object> corpo) throws Exception {
        return post("/admin/events")
                .header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo));
    }

    private String publicarEObterId() throws Exception {
        String id = criarEObterId();
        mockMvc.perform(post("/admin/events/" + id + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin()))
                .andExpect(status().isOk());
        return id;
    }

    private String criarEObterId() throws Exception {
        String corpo = mockMvc.perform(criar(corpoValido()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("id").asText();
    }

    /** Cabecalho Authorization com um token de ADMIN valido. */
    private String autorizacaoAdmin() {
        return "Bearer " + GeradorDeToken.deAdmin();
    }
}
