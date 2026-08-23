package com.devbandeiraa.shared.security;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Identificador que acompanha uma requisicao por todos os servicos que ela atravessa.
 *
 * <p>O problema que resolve: um pedido de reserva passa pelo gateway, chega ao booking-service e
 * de la vira uma chamada ao event-service. Sao tres linhas de log em tres containers diferentes,
 * e sem um identificador comum nao ha como saber que pertencem ao mesmo pedido — a investigacao
 * vira comparacao de horarios, que falha justamente sob carga, quando ha dezenas de requisicoes
 * por segundo e os relogios dos containers nao estao perfeitamente alinhados.
 *
 * <p>Constantes e regras reunidas aqui, e nao no filtro, porque ha <em>dois</em> filtros: um
 * reativo no gateway e um de servlet nos servicos. Se cada um definisse o proprio nome de
 * cabecalho, bastaria uma divergencia de maiuscula para a corrente se partir em silencio.
 */
public final class CorrelacaoDeRequisicao {

    /** Nome do cabecalho. Convencao amplamente usada, o que ajuda quem le sem conhecer o projeto. */
    public static final String CABECALHO = "X-Request-Id";

    /** Chave no MDC. E o que permite ao padrao de log imprimir o id sem passa-lo de metodo em metodo. */
    public static final String CHAVE_MDC = "requestId";

    /**
     * Formato aceito de quem chega de fora.
     *
     * <p>Esta validacao existe por seguranca, nao por capricho. O valor recebido vai parar em toda
     * linha de log da requisicao; aceita-lo cru permitiria a qualquer cliente injetar quebras de
     * linha e escrever no log entradas falsas, com aparencia de terem sido produzidas pelo
     * servico. E o ataque de log forging, e o remedio e nao confiar no que veio pela rede.
     *
     * <p>O teto de 64 caracteres tambem limita quanto um cliente consegue fazer o servico gravar
     * em disco por requisicao.
     */
    private static final Pattern FORMATO_ACEITO = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private static final HexFormat HEXADECIMAL = HexFormat.of();

    private CorrelacaoDeRequisicao() {
    }

    /**
     * Devolve o id recebido quando ele e aceitavel, ou um novo quando nao e.
     *
     * <p>Aceitar o id do cliente e deliberado: se um dia houver um balanceador ou um app movel que
     * ja gera o proprio, a corrente continua inteira em vez de comecar de novo na borda. Um id
     * malformado nao vira erro — a requisicao e legitima, so o cabecalho e que nao serve, e
     * recusar a chamada por causa disso trocaria um problema de observabilidade por um de
     * disponibilidade.
     */
    public static String normalizar(String recebido) {
        return aceitavel(recebido) ? recebido : gerar();
    }

    public static boolean aceitavel(String valor) {
        return valor != null && FORMATO_ACEITO.matcher(valor).matches();
    }

    /**
     * Dezesseis caracteres hexadecimais: curto o bastante para caber no comeco de cada linha de log
     * sem atrapalhar a leitura, e largo o bastante para nao repetir dentro de uma investigacao.
     *
     * <p>{@code ThreadLocalRandom} e nao {@code SecureRandom}: o id nao e credencial nenhuma, e
     * torna-lo imprevisivel nao protegeria nada — quem quisesse forjar um ja pode simplesmente
     * enviar o cabecalho. O que se ganha e evitar disputa por uma instancia compartilhada em cada
     * requisicao, que e exatamente o custo que um gateway nao deve pagar.
     */
    public static String gerar() {
        byte[] bytes = new byte[8];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HEXADECIMAL.formatHex(bytes);
    }
}
