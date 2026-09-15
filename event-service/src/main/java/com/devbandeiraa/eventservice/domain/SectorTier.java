package com.devbandeiraa.eventservice.domain;

/**
 * Faixa de um setor.
 *
 * <p>Diz se o setor e uma oferta comum ou diferenciada. Serve a duas telas: o seletor de setores,
 * que destaca a faixa superior, e o mapa de assentos, onde um lugar VIP precisa se distinguir de
 * um lugar comum antes mesmo de o usuario ler o preco.
 *
 * <p>Nao e derivada do preco. "Mais caro" e "VIP" coincidem com frequencia e nao sao a mesma
 * coisa: uma casa pode ter Camarote e Frisa no mesmo patamar, ou um setor caro apenas por ficar
 * perto do palco, sem beneficio algum atrelado. Deduzir do preco transformaria uma decisao de
 * quem cadastra em consequencia aritmetica.
 */
public enum SectorTier {

    /** Oferta comum. Valor padrao de quem nao declara nada. */
    STANDARD,

    /** Oferta diferenciada, normalmente com beneficios declarados em {@link Sector}. */
    VIP
}
