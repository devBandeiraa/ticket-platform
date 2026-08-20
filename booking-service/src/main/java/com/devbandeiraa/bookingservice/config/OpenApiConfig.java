package com.devbandeiraa.bookingservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacao OpenAPI do booking-service.
 *
 * <p>A descricao explica onde mora a garantia contra overselling. Nao e enfeite: quem le esta
 * API precisa saber que um {@code 409 SOLD_OUT} nao e falha temporaria a ser repetida, e sim a
 * resposta correta de quem perdeu a corrida pelo ultimo ingresso.
 */
@Configuration
public class OpenApiConfig {

    /** Nome do esquema, referenciado pelas anotacoes nos controllers. */
    public static final String ESQUEMA_JWT = "bearer-jwt";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("booking-service")
                        .version("0.0.1")
                        .description("""
                                Reservas, estoque e pagamento — o nucleo da plataforma.

                                Uma reserva nasce `PENDING` com prazo para pagamento e segura o \
                                ingresso durante esse tempo. Paga, vira `CONFIRMED`; vencida sem \
                                pagamento, vira `EXPIRED` e o ingresso volta ao estoque.

                                **Sobre concorrencia.** A garantia de nunca vender alem da \
                                capacidade esta em um `UPDATE` condicional protegido por uma \
                                `CHECK constraint` no PostgreSQL, e nao no lock distribuido — o \
                                lock e otimizacao. Quem perde a corrida recebe `409 SOLD_OUT`, \
                                que e resposta definitiva e nao deve ser repetida.

                                **Sobre idempotencia.** `POST /bookings` exige o cabecalho \
                                `Idempotency-Key`. Repetir a chamada com a mesma chave devolve a \
                                reserva ja criada com `200`, em vez de criar uma segunda com \
                                `201` — e o que torna seguro repetir um pedido que expirou por \
                                timeout sem saber se chegou."""))
                .servers(servidores())
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, esquemaJwt()));
    }

    /**
     * Os dois enderecos por onde este servico pode ser alcancado.
     *
     * <p>Sao relativos de proposito: o navegador resolve cada um contra a origem de onde a
     * pagina do Swagger veio. Aberta no gateway, {@code /api} aponta para o gateway; aberta
     * direto no servico, {@code /} aponta para o servico. Um endereco absoluto so funcionaria
     * em um dos dois casos, e quebraria no outro.
     */
    private static List<Server> servidores() {
        return List.of(
                new Server().url("/api").description("atraves do api-gateway (padrao)"),
                new Server().url("/").description("acesso direto ao servico"));
    }

    private static SecurityScheme esquemaJwt() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Cole aqui o `accessToken` devolvido por `POST /auth/login`. "
                        + "O id do usuario sai sempre do token, nunca do corpo ou da URL.");
    }
}
