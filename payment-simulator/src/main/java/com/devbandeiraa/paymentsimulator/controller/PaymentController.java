package com.devbandeiraa.paymentsimulator.controller;

import com.devbandeiraa.paymentsimulator.dto.PaymentRequest;
import com.devbandeiraa.paymentsimulator.dto.PaymentResponse;
import com.devbandeiraa.paymentsimulator.service.AutorizadorSimulado;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A unica rota deste servico.
 *
 * <p>Sem autenticacao de proposito. Ele nao e publicado pelo gateway e so responde de dentro da
 * rede do compose; acrescentar JWT aqui simularia mal um provedor externo, que autenticaria por
 * chave de API e nao pelo token do usuario final da nossa plataforma.
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final AutorizadorSimulado autorizador;

    public PaymentController(AutorizadorSimulado autorizador) {
        this.autorizador = autorizador;
    }

    /**
     * Cobra, ou explica por que nao cobrou.
     *
     * <p>O {@code Idempotency-Key} e obrigatorio e vem do cliente. E ele que separa "repetir a
     * mesma cobranca" de "cobrar de novo" — sem o cabecalho nao ha como distinguir as duas coisas,
     * e um retry viraria cobranca em dobro.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> cobrar(
            @Valid @RequestBody PaymentRequest requisicao,
            @RequestHeader("Idempotency-Key") String chaveDeIdempotencia) {

        return ResponseEntity.ok(autorizador.cobrar(requisicao, chaveDeIdempotencia));
    }

    /**
     * Cancela uma autorizacao.
     *
     * <p>Usado pelo booking-service quando a cobranca passa mas a reserva expirou no meio do
     * caminho. Devolve 204 mesmo para um estorno repetido: quem compensa uma falha ja esta num
     * caminho que deu errado, e nao pode receber um erro novo por tentar consertar.
     */
    @PostMapping("/{comprovante}/void")
    public ResponseEntity<Void> estornar(@PathVariable String comprovante) {
        if (!autorizador.estornar(comprovante)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
