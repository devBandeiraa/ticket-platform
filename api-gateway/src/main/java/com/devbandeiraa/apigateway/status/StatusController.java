package com.devbandeiraa.apigateway.status;

import com.devbandeiraa.shared.security.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * O unico endpoint que o gateway responde por conta propria, em vez de encaminhar.
 *
 * <h2>Como ele escapa do roteamento</h2>
 *
 * <p>Nenhuma rota do {@code application.yml} casa com {@code /api/status}, entao a requisicao cai
 * no controller. Isso tem duas consequencias que valem estar escritas, porque nao sao obvias
 * olhando so este arquivo:
 *
 * <ul>
 *   <li>o {@code StripPrefix=1} nao se aplica — dai o {@code /api} literal no mapeamento;
 *   <li>os {@code GlobalFilter} do gateway tambem nao rodam, o que deixa este caminho fora da
 *       autenticacao na borda e do rate limiter. E aceitavel para uma pagina de status de um
 *       projeto de estudo, e e deliberado: um painel que responde 429 justamente quando alguem
 *       corre para ver o que esta acontecendo nao serve para nada.
 * </ul>
 *
 * <p>Num ambiente publico isto mudaria. A resposta revela o nome de cada servico e quais estao
 * fora — informacao util para quem esta atacando — e o caminho passaria a exigir sessao de
 * administrador.
 */
@RestController
// O globalcors do Spring Cloud Gateway configura o handler mapping das ROTAS, e este endpoint nao
// e uma delas. Sem isto o navegador barraria a chamada no preflight, com a pagina reportando
// "servidor fora do ar" enquanto tudo esta no ar.
@CrossOrigin(origins = "${CORS_ALLOWED_ORIGINS:http://localhost:5173}")
public class StatusController {

    private static final Logger log = LoggerFactory.getLogger(StatusController.class);

    private final ColetorDeStatus coletor;

    public StatusController(ColetorDeStatus coletor) {
        this.coletor = coletor;
    }

    @GetMapping("/api/status")
    public Mono<StatusDaPlataforma> status() {
        return coletor.coletar();
    }

    /**
     * Sem metricas, a resposta e um erro — e nao um retrato vazio.
     *
     * <p>Devolver a estrutura com todos os servicos marcados como fora seria mentir com precisao:
     * a tela ficaria vermelha, e o que esta fora e o Prometheus. Um 503 com codigo proprio permite
     * ao painel dizer que perdeu a fonte, sem acusar ninguem.
     */
    @ExceptionHandler(MetricasIndisponiveisException.class)
    public ResponseEntity<ApiError> semMetricas(
            MetricasIndisponiveisException falha, ServerWebExchange troca) {

        log.warn("painel de status sem fonte de metricas: {}", falha.getMessage());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.de(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "METRICS_UNAVAILABLE",
                        "Nao foi possivel ler as metricas da plataforma",
                        troca.getRequest().getPath().value(),
                        null));
    }
}
