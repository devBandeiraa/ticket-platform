package com.devbandeiraa.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Corpo que o Jackson nao consegue ler devolve 400, e nao 500.
 *
 * <p>O caso foi encontrado na Fase 22 por um teste do booking-service: enviar
 * {@code {"method": "BOLETO"}} produzia 500. A leitura falha antes do controller, entao nem a
 * validacao do Bean Validation roda, e nada entre o Jackson e a rede de seguranca sabia que o
 * erro era de quem enviou.
 *
 * <p>O teste mora aqui, e nao no servico onde o defeito apareceu, porque o tratador e comum aos
 * quatro. Verificado la, a regressao passaria despercebida nos outros tres.
 *
 * <p>Montagem standalone, sem contexto do Spring Boot: o que se verifica e o tratador, e subir
 * uma aplicacao inteira acrescentaria dezenas de beans que nao participam da resposta.
 */
class CorpoIlegivelTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ControladorDeTeste())
            .setControllerAdvice(new TratadorDeTeste())
            .build();

    @Test
    @DisplayName("valor fora do conjunto de um enum devolve 400 com MALFORMED_BODY")
    void enumInvalidoDeveDevolver400() throws Exception {
        mockMvc.perform(post("/teste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cor\":\"ROXO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_BODY"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("JSON sintaticamente quebrado devolve 400")
    void jsonQuebradoDeveDevolver400() throws Exception {
        mockMvc.perform(post("/teste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cor\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_BODY"));
    }

    @Test
    @DisplayName("tipo errado num campo devolve 400")
    void tipoErradoDeveDevolver400() throws Exception {
        mockMvc.perform(post("/teste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cor\":\"AZUL\",\"quantidade\":\"muitas\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_BODY"));
    }

    @Test
    @DisplayName("a resposta nao vaza a lista de valores aceitos nem o nome da classe do enum")
    void naoDeveVazarDetalheInterno() throws Exception {
        // A mensagem do Jackson traz `com.devbandeiraa...Cor` e todos os valores do enum. E mais
        // do que o cliente precisa e mais do que convem publicar; o detalhe fica no log.
        mockMvc.perform(post("/teste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cor\":\"ROXO\"}"))
                .andExpect(jsonPath("$.message").value("O corpo da requisicao nao pode ser lido"));
    }

    // ---------- apoio ----------

    private enum Cor {
        AZUL,
        VERDE
    }

    private record CorpoDeTeste(Cor cor, Integer quantidade) {
    }

    @RestController
    private static class ControladorDeTeste {

        @PostMapping("/teste")
        String receber(@RequestBody CorpoDeTeste corpo) {
            return corpo.cor().name();
        }
    }

    /** O tratador e abstrato; esta subclasse existe so para instancia-lo. */
    @RestControllerAdvice
    private static class TratadorDeTeste extends ApiExceptionHandlerSupport {
    }
}
