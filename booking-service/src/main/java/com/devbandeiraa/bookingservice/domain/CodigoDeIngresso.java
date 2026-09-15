package com.devbandeiraa.bookingservice.domain;

import java.security.SecureRandom;

/**
 * Numero do ingresso: {@code TP-XXXXXX-XXXXXX}.
 *
 * <p>E o identificador que a pessoa le, dita no telefone e mostra na portaria, e tambem o que vai
 * dentro do QR Code. Nao substitui o id da reserva, que continua sendo a chave — mas um UUID nao
 * se dita, e e por isso que existe um segundo identificador.
 *
 * <h2>Aleatorio, e nao sequencial</h2>
 *
 * <p>Sequencial seria unico de graca e entregaria o volume de vendas: bastaria comprar dois
 * ingressos e olhar a diferenca entre os numeros para saber quantos sairam no intervalo.
 *
 * <h2>O alfabeto</h2>
 *
 * <p>Trinta e um simbolos: os dez digitos e as vinte e seis letras, menos {@code I}, {@code O},
 * {@code Q}, {@code S} e {@code Z}. Os cinco saem porque se confundem com {@code 1}, {@code 0} e
 * {@code 2} quando alguem le em voz alta ou digita a partir de uma tela — e este codigo existe
 * justamente para ser lido e digitado. Excluir tambem os digitos parecidos resolveria a confusao
 * pelo outro lado, e custaria mais entropia do que excluir as letras.
 *
 * <h2>Colisao</h2>
 *
 * <p>{@code 31^12} da cerca de {@code 7,9 x 10^17} combinacoes. Pelo paradoxo do aniversario, a
 * chance de duas coincidirem em um milhao de ingressos e da ordem de {@code 6 x 10^-7}. Nao ha
 * retentativa aqui: o indice unico no banco e a garantia, e uma colisao devolve erro em vez de
 * gravar dois ingressos com o mesmo numero. No dia em que a escala tornar isso frequente, o que
 * muda e o comprimento do codigo, e nao um laco de tentativas.
 */
public final class CodigoDeIngresso {

    private static final String ALFABETO = "0123456789ABCDEFGHJKLMNPRTUVWXY";

    private static final int SIMBOLOS_POR_GRUPO = 6;

    private static final String PREFIXO = "TP";

    /**
     * {@link SecureRandom} e nao {@link java.util.Random}.
     *
     * <p>O codigo vale como prova de compra na entrada. Com um gerador previsivel, quem observasse
     * alguns codigos poderia deduzir os proximos e apresentar um ingresso que nunca foi vendido.
     */
    private static final SecureRandom SORTEIO = new SecureRandom();

    private CodigoDeIngresso() {
    }

    public static String gerar() {
        return PREFIXO + "-" + grupo() + "-" + grupo();
    }

    private static String grupo() {
        StringBuilder simbolos = new StringBuilder(SIMBOLOS_POR_GRUPO);

        for (int i = 0; i < SIMBOLOS_POR_GRUPO; i++) {
            simbolos.append(ALFABETO.charAt(SORTEIO.nextInt(ALFABETO.length())));
        }

        return simbolos.toString();
    }
}
