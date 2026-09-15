package com.devbandeiraa.bookingservice.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * O dinheiro de uma reserva: subtotal, taxa e total.
 *
 * <p>Existe para que {@code total = subtotal + taxa} seja verdade num lugar so. Com os tres
 * valores soltos, bastaria um caminho de escrita calcular a taxa de forma diferente para o total
 * deixar de fechar — e o banco recusaria a linha pelo {@code ck_bookings_total_fecha}, o que e
 * melhor do que gravar incoerencia, mas o erro apareceria longe de onde nasceu.
 *
 * @param subtotal soma dos precos dos lugares tomados
 * @param taxa     taxa de servico sobre o subtotal
 * @param total    o que sera cobrado
 */
public record Valores(BigDecimal subtotal, BigDecimal taxa, BigDecimal total) {

    /** Casas decimais do dinheiro, iguais as de {@code NUMERIC(10, 2)} no banco. */
    private static final int CASAS = 2;

    private static final BigDecimal CEM = new BigDecimal("100");

    /**
     * Calcula a taxa sobre o subtotal e fecha o total.
     *
     * <p>Arredonda com {@code HALF_UP}, a regra que as pessoas aprendem na escola e esperam ver:
     * meio centavo sobe. {@code HALF_EVEN} distribui melhor o vies em somas grandes e produziria,
     * na conta de um comprador so, um arredondamento que ele nao consegue reproduzir de cabeca.
     *
     * <p>Arredonda a TAXA, e nao o total. Arredondar o total deixaria a diferenca entre ele e a
     * soma das partes sem dono: o comprador veria subtotal e taxa que nao somam o que esta sendo
     * cobrado. Assim o centavo do arredondamento fica visivelmente dentro da taxa.
     *
     * @param percentual taxa em pontos percentuais; zero desliga a cobranca
     */
    public static Valores de(BigDecimal subtotal, BigDecimal percentual) {
        BigDecimal taxa = subtotal
                .multiply(percentual)
                .divide(CEM, CASAS, RoundingMode.HALF_UP);

        // UNNECESSARY no subtotal e no total de proposito: os dois vem de somar precos que a
        // coluna ja guarda com duas casas, entao fixar a escala aqui nunca deveria arredondar
        // nada. Se um dia arredondar, e porque um preco entrou com mais casas em algum lugar —
        // e uma excecao apontando para ca e melhor do que um centavo somindo em silencio.
        return new Valores(subtotal.setScale(CASAS, RoundingMode.UNNECESSARY), taxa,
                subtotal.add(taxa).setScale(CASAS, RoundingMode.UNNECESSARY));
    }
}
