package com.devbandeiraa.bookingservice.dto.response;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Agregados de venda, para o painel de quem organiza.
 *
 * <p>Vem de {@code COUNT} e {@code SUM} sobre as tabelas deste servico, e nao das metricas do
 * Micrometer. Sao perguntas diferentes: o Prometheus conta o que ESTE processo viu desde que
 * subiu, e zera a cada reinicio; um painel de vendas precisa do total historico, que so o banco
 * tem. As duas fontes convivem e medem coisas distintas.
 *
 * @param receitaDosIngressos soma dos subtotais confirmados — o que os ingressos renderam
 * @param taxaArrecadada      soma das taxas confirmadas — o que a plataforma reteve. Separado da
 *                            receita de proposito: somados, viram um numero que nao responde nem
 *                            a pergunta do organizador nem a da plataforma
 * @param ingressosVendidos   lugares em reservas confirmadas
 * @param reservasCriadas     todas as reservas, em qualquer estado
 * @param reservasConfirmadas as que chegaram a pagamento
 * @param reservasExpiradas   as que venceram o prazo sem pagar
 * @param reservasCanceladas  as desistencias deliberadas
 * @param conversao           confirmadas sobre criadas, em pontos percentuais
 * @param lugaresDisponiveis  lugares ainda livres. <strong>Conta apenas eventos ja hidratados
 *                            neste servico</strong> — um evento publicado que nunca recebeu
 *                            tentativa de reserva ainda nao tem assentos aqui, e nao entra
 */
public record MetricasResponse(
        BigDecimal receitaDosIngressos,
        BigDecimal taxaArrecadada,
        long ingressosVendidos,
        long reservasCriadas,
        long reservasConfirmadas,
        long reservasExpiradas,
        long reservasCanceladas,
        BigDecimal conversao,
        long lugaresDisponiveis) {

    private static final BigDecimal CEM = new BigDecimal("100");

    public static MetricasResponse de(AgregadoDeReservas reservas, long lugaresDisponiveis) {
        return new MetricasResponse(
                ouZero(reservas.getReceitaDosIngressos()),
                ouZero(reservas.getTaxaArrecadada()),
                reservas.getIngressosVendidos(),
                reservas.getReservasCriadas(),
                reservas.getReservasConfirmadas(),
                reservas.getReservasExpiradas(),
                reservas.getReservasCanceladas(),
                conversao(reservas.getReservasConfirmadas(), reservas.getReservasCriadas()),
                lugaresDisponiveis);
    }

    /**
     * Confirmadas sobre criadas, em pontos percentuais.
     *
     * <p>Sem reserva alguma devolve zero, e nao erro nem nulo. Dividir por zero seria o caso de
     * uma plataforma recem-instalada, e um painel que quebra no primeiro acesso e pior do que um
     * painel mostrando 0%.
     */
    private static BigDecimal conversao(long confirmadas, long criadas) {
        if (criadas == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(confirmadas)
                .multiply(CEM)
                .divide(BigDecimal.valueOf(criadas), 1, RoundingMode.HALF_UP);
    }

    /** {@code SUM} sobre conjunto vazio devolve nulo no SQL, e a tela espera um numero. */
    private static BigDecimal ouZero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO.setScale(2) : valor;
    }

    /**
     * Projecao da consulta agregada.
     *
     * <p>Interface, e nao record: o Spring Data monta a implementacao a partir dos aliases da
     * consulta nativa. Com record, cada coluna teria de casar por posicao, e trocar duas delas de
     * lugar na consulta produziria numeros trocados sem erro algum.
     */
    public interface AgregadoDeReservas {

        BigDecimal getReceitaDosIngressos();

        BigDecimal getTaxaArrecadada();

        long getIngressosVendidos();

        long getReservasCriadas();

        long getReservasConfirmadas();

        long getReservasExpiradas();

        long getReservasCanceladas();
    }
}
