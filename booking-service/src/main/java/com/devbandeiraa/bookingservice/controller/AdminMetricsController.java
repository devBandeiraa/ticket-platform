package com.devbandeiraa.bookingservice.controller;

import com.devbandeiraa.bookingservice.dto.response.MetricasResponse;
import com.devbandeiraa.bookingservice.service.MetricasDeVendaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agregados de venda para o painel de quem organiza.
 *
 * <p>Sob {@code /admin}, entao a autorizacao ja vem da regra unica de prefixo no
 * {@code SecurityConfig} — nao ha anotacao por metodo que alguem possa esquecer de colocar.
 *
 * <p>Separado do {@code AdminBookingController} de proposito: aquele lista reservas, este
 * responde por totais. Sao leituras de natureza diferente — uma pagina, a outra agrega a tabela
 * inteira — e juntas num controller so, a proxima metrica entraria ao lado de um metodo de
 * listagem sem que nada indicasse que sao assuntos distintos.
 */
@RestController
@RequestMapping("/admin/metrics")
@Tag(name = "Administracao — metricas",
        description = "Totais de venda agregados do banco. Exige papel ADMIN.")
public class AdminMetricsController {

    private final MetricasDeVendaService metricas;

    public AdminMetricsController(MetricasDeVendaService metricas) {
        this.metricas = metricas;
    }

    @Operation(summary = "Agregados de venda",
            description = "Receita, ingressos vendidos, reservas por estado, conversao e "
                    + "lugares disponiveis.\n\n"
                    + "Vem de `COUNT` e `SUM` sobre o banco, e **nao** das metricas do "
                    + "Prometheus: aquelas contam o que o processo viu desde que subiu e zeram "
                    + "a cada reinicio, enquanto um painel de vendas precisa do historico.\n\n"
                    + "`lugaresDisponiveis` conta apenas eventos ja hidratados neste servico. "
                    + "Um evento publicado que nunca recebeu tentativa de reserva ainda nao tem "
                    + "assentos aqui e nao entra na conta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "agregados no instante da consulta"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: token sem papel ADMIN",
                    content = @Content)})
    @GetMapping
    public ResponseEntity<MetricasResponse> agregados() {
        return ResponseEntity.ok(metricas.agregar());
    }
}
