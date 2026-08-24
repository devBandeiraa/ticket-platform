package com.devbandeiraa.bookingservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Comprovante devolvido pelo provedor de pagamento.
 *
 * <p>{@code ignoreUnknown} porque este corpo vem de um terceiro: um campo novo do lado de la nao
 * pode quebrar a reserva do lado de ca. E a mesma razao pela qual o provedor nao compartilha o
 * {@code ApiError} da plataforma — ele nao e nosso.
 *
 * @param authorizationCode identificador da cobranca, necessario para um eventual estorno
 * @param repetida          se o provedor reconheceu a chave e devolveu a cobranca ja feita
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Autorizacao(UUID bookingId, String authorizationCode, boolean repetida) {
}
