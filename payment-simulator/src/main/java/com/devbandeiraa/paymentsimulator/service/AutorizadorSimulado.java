package com.devbandeiraa.paymentsimulator.service;

import com.devbandeiraa.paymentsimulator.config.SimulacaoProperties;
import com.devbandeiraa.paymentsimulator.dto.PaymentRequest;
import com.devbandeiraa.paymentsimulator.dto.PaymentResponse;
import com.devbandeiraa.paymentsimulator.exception.PagamentoIndisponivelException;
import com.devbandeiraa.paymentsimulator.exception.PagamentoRecusadoException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decide o destino de cada cobranca e lembra o que ja decidiu.
 *
 * <h2>A idempotencia e o ponto todo deste servico</h2>
 *
 * <p>Retry num pagamento e uma ideia perigosa. Se a primeira tentativa estourou por timeout, quem
 * chamou nao sabe se a cobranca passou — e repetir as cegas pode cobrar duas vezes de uma pessoa
 * de verdade. O que torna o retry seguro nao e a configuracao do Resilience4j do lado de la; e
 * <em>este</em> lado garantir que a mesma chave nunca cobre duas vezes.
 *
 * <p>Por isso a chave de idempotencia manda em tudo: a primeira chamada com uma chave sorteia o
 * desfecho e o guarda; qualquer repeticao devolve o mesmo comprovante sem sortear de novo. O
 * cliente pode repetir a vontade.
 *
 * <h2>O que se registra e o que nao se registra</h2>
 *
 * <p>Desfechos definitivos — autorizado e recusado — ficam guardados. Falha transitoria, nao: ela
 * significa que a cobranca nem chegou a ser avaliada, e guarda-la condenaria a chave a falhar para
 * sempre, tornando o retry inutil justamente no caso em que ele deveria funcionar.
 */
@Service
public class AutorizadorSimulado {

    private static final Logger log = LoggerFactory.getLogger(AutorizadorSimulado.class);

    /**
     * Em memoria de proposito: e um simulador, e um banco aqui so acrescentaria infraestrutura a
     * um servico cujo papel e ser descartavel. O limite fica registrado no README.
     */
    private final Map<String, PaymentResponse> autorizacoes = new ConcurrentHashMap<>();
    private final Map<String, String> recusas = new ConcurrentHashMap<>();
    private final Set<String> estornados = ConcurrentHashMap.newKeySet();

    private final SimulacaoProperties propriedades;

    public AutorizadorSimulado(SimulacaoProperties propriedades) {
        this.propriedades = propriedades;
    }

    public PaymentResponse cobrar(PaymentRequest requisicao, String chaveDeIdempotencia) {
        PaymentResponse jaAutorizada = autorizacoes.get(chaveDeIdempotencia);
        if (jaAutorizada != null) {
            log.info("cobranca repetida da chave '{}': devolvendo a autorizacao {}",
                    chaveDeIdempotencia, jaAutorizada.authorizationCode());
            return new PaymentResponse(
                    jaAutorizada.bookingId(), jaAutorizada.authorizationCode(), true);
        }

        String recusadaAntes = recusas.get(chaveDeIdempotencia);
        if (recusadaAntes != null) {
            // Uma recusa tambem e definitiva. Deixar a repeticao sortear de novo faria o mesmo
            // cartao ora passar ora nao, e quem integra concluiria que o retry "as vezes resolve"
            // uma recusa — a licao exatamente oposta a que este simulador existe para ensinar.
            throw new PagamentoRecusadoException(recusadaAntes);
        }

        atrasar();

        int sorteio = ThreadLocalRandom.current().nextInt(100);

        if (sorteio < propriedades.falhaPercentual()) {
            // Nao registrada: a cobranca nao chegou a ser avaliada, e a proxima tentativa com esta
            // mesma chave deve ter uma chance nova.
            log.warn("falha transitoria na cobranca da reserva {} (chave '{}')",
                    requisicao.bookingId(), chaveDeIdempotencia);
            throw new PagamentoIndisponivelException("o provedor de pagamento nao respondeu");
        }

        if (sorteio < propriedades.falhaPercentual() + propriedades.recusaPercentual()) {
            String motivo = "fundos insuficientes";
            recusas.put(chaveDeIdempotencia, motivo);

            log.info("cobranca recusada para a reserva {} (chave '{}'): {}",
                    requisicao.bookingId(), chaveDeIdempotencia, motivo);
            throw new PagamentoRecusadoException(motivo);
        }

        PaymentResponse autorizada = new PaymentResponse(
                requisicao.bookingId(), gerarComprovante(), false);
        autorizacoes.put(chaveDeIdempotencia, autorizada);

        log.info("cobranca autorizada: reserva={} valor={} comprovante={}",
                requisicao.bookingId(), requisicao.amount(), autorizada.authorizationCode());

        return autorizada;
    }

    /**
     * Cancela uma autorizacao concedida.
     *
     * <p>Existe por causa de um caso que o booking-service nao consegue evitar: a cobranca passa,
     * e no instante seguinte a reserva ja expirou. Sem estorno, sobraria dinheiro cobrado sem
     * ingresso — e o servico teria trocado um problema de disponibilidade por um problema de
     * dinheiro, que e muito pior.
     *
     * <p>Idempotente: estornar o que ja foi estornado nao e erro. Quem compensa uma falha esta,
     * por definicao, num caminho que ja deu errado uma vez, e nao pode receber um erro novo por
     * tentar consertar.
     *
     * @return {@code false} se o comprovante nunca existiu
     */
    public boolean estornar(String comprovante) {
        if (estornados.contains(comprovante)) {
            log.debug("estorno repetido do comprovante {}", comprovante);
            return true;
        }

        boolean existe = autorizacoes.values().stream()
                .anyMatch(autorizacao -> autorizacao.authorizationCode().equals(comprovante));

        if (!existe) {
            log.warn("estorno pedido para comprovante desconhecido: {}", comprovante);
            return false;
        }

        estornados.add(comprovante);
        log.info("autorizacao estornada: {}", comprovante);
        return true;
    }

    /**
     * Atraso proposital.
     *
     * <p>Sem ele, o retry seria invisivel: as tentativas sairiam e voltariam no mesmo milissegundo,
     * e nem o grafico de latencia nem o tracing da fase seguinte teriam o que mostrar. Um
     * provedor de pagamento real leva centenas de milissegundos, e e esse tempo que faz o timeout
     * de quem chama ser uma decisao de verdade.
     */
    private void atrasar() {
        long base = propriedades.latencia().toMillis();
        long extra = propriedades.latenciaExtra().toMillis();
        long total = base + (extra > 0 ? ThreadLocalRandom.current().nextLong(extra) : 0);

        if (total <= 0) {
            return;
        }

        try {
            Thread.sleep(total);
        } catch (InterruptedException interrompida) {
            // Restaurar a marca e obrigatorio: engoli-la faria o servidor perder o pedido de
            // desligamento e o container so morreria no timeout do Docker.
            Thread.currentThread().interrupt();
            throw new PagamentoIndisponivelException("cobranca interrompida durante o processamento");
        }
    }

    private String gerarComprovante() {
        byte[] bytes = new byte[6];
        ThreadLocalRandom.current().nextBytes(bytes);
        return "AUT-" + HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
