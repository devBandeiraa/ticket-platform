package com.devbandeiraa.eventservice.repository;

import com.devbandeiraa.eventservice.domain.Event;
import com.devbandeiraa.eventservice.domain.EventStatus;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros combinaveis da listagem de eventos.
 *
 * <p>A alternativa seria uma unica consulta JPQL com {@code (:parametro IS NULL OR ...)} para
 * cada filtro opcional. Isso nao funciona contra o PostgreSQL: quando o parametro aparece apenas
 * dentro de um teste de nulidade, o banco nao consegue inferir o tipo do bind e a consulta falha
 * com <em>could not determine data type of parameter</em>.
 *
 * <p>Com Specifications o problema deixa de existir: um filtro ausente simplesmente nao entra na
 * consulta, em vez de entrar como uma comparacao com nulo. O SQL gerado tambem fica mais enxuto,
 * carregando so as condicoes realmente em uso.
 */
public final class EventSpecifications {

    private EventSpecifications() {
    }

    public static Specification<Event> comStatus(EventStatus status) {
        return (raiz, consulta, construtor) -> construtor.equal(raiz.get("status"), status);
    }

    /** Busca por trecho do nome, indiferente a caixa. */
    public static Specification<Event> comNomeContendo(String trecho) {
        return (raiz, consulta, construtor) -> construtor.like(
                construtor.lower(raiz.get("name")),
                "%" + trecho.toLowerCase() + "%");
    }

    public static Specification<Event> aPartirDe(Instant data) {
        return (raiz, consulta, construtor) ->
                construtor.greaterThanOrEqualTo(raiz.get("eventDate"), data);
    }

    public static Specification<Event> ate(Instant data) {
        return (raiz, consulta, construtor) ->
                construtor.lessThanOrEqualTo(raiz.get("eventDate"), data);
    }
}
