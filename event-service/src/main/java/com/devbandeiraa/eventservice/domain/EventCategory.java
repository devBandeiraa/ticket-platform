package com.devbandeiraa.eventservice.domain;

/**
 * Categoria de um evento no catalogo.
 *
 * <p>Existe para o catalogo ser navegavel. Sem ela a unica forma de encontrar um evento e digitar
 * parte do nome, o que atende quem ja sabe o que procura e ignora quem chegou sem saber.
 *
 * <p>Persistido como texto, e nao pelo ordinal, pela mesma razao de {@link EventStatus}: inserir
 * uma categoria no meio do enum nao pode reescrever o significado das linhas ja gravadas.
 *
 * <p>As seis sao fechadas de proposito. Categoria livre viraria, em poucos meses, "Show", "show",
 * "Shows" e "Musica" convivendo — e ai o filtro do catalogo deixa de filtrar. Quando uma setima
 * fizer falta, ela entra aqui e no CHECK da migration, que e onde se enxerga o conjunto inteiro.
 */
public enum EventCategory {

    /** Apresentacao musical de um artista ou grupo, em casa fechada. */
    SHOWS,

    /** Varios atracoes ao longo de um ou mais dias, normalmente ao ar livre. */
    FESTIVAIS,

    ESPORTES,

    /** Conferencia, meetup, hackathon. */
    TECNOLOGIA,

    /** Teatro, danca, stand-up e demais artes cenicas. */
    TEATRO,

    FESTAS
}
