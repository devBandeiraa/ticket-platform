package com.devbandeiraa.apigateway.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;

/**
 * Monta o retrato da plataforma a partir de quatro consultas ao Prometheus.
 *
 * <h2>Por que aqui, e nao no navegador</h2>
 *
 * <p>A alternativa seria a tela consultar o Prometheus direto. Ela funcionaria — e obrigaria a
 * publicar o Prometheus para a internet, o que entrega de bandeja o nome de cada servico, cada
 * endpoint e cada metrica interna a quem abrir o endereco. O gateway ja e o unico endereco que o
 * navegador conhece nesta plataforma; manter isso vale mais que economizar uma classe.
 *
 * <p>Sao quatro consultas, e nao uma: o Prometheus nao tem API de lote. Elas saem em paralelo, de
 * modo que o custo e o da mais lenta, e nao a soma das quatro.
 */
@Service
public class ColetorDeStatus {

    /**
     * Quem existe e quem respondeu.
     *
     * <p>A lista de servicos sai daqui, e nao de uma constante no codigo. E o que faz um servico
     * novo aparecer na tela por ter sido acrescentado ao {@code prometheus.yml} — sem isso,
     * incluir um servico exigiria lembrar de dois lugares, e o segundo seria esquecido.
     */
    private static final String NO_AR = "up";

    /**
     * Latencia media da janela, em segundos.
     *
     * <p>Media, e nao o p95 dos paineis do Grafana, por uma razao de audiencia: quem abre esta
     * pagina quer saber se o sistema esta respondendo, nao investigar a cauda da distribuicao. O
     * p95 continua no Grafana, que e onde se investiga.
     *
     * <p>Janela de cinco minutos porque a pagina tambem serve para plataformas ociosas: com um
     * minuto, alguns segundos sem trafego ja apagariam o numero.
     */
    private static final String LATENCIA_MEDIA = """
            sum by (job) (rate(http_server_requests_seconds_sum[5m]))
              / sum by (job) (rate(http_server_requests_seconds_count[5m]))""";

    /** Ha quanto tempo o processo esta de pe. Zera a cada restart, que e justamente o sinal. */
    private static final String UPTIME = "process_uptime_seconds";

    /**
     * Estado de cada circuito, colapsado num numero.
     *
     * <p>O Resilience4j publica um gauge por estado possivel, valendo 1 apenas no atual. Somar
     * assim troca quatro series binarias por uma escala legivel: 0 fechado, 1 meio aberto, 2
     * aberto — que e o que {@link #estadoDe} traduz.
     */
    private static final String ESTADO_DOS_CIRCUITOS = """
            sum by (name) (resilience4j_circuitbreaker_state{state="open"}) * 2
              + sum by (name) (resilience4j_circuitbreaker_state{state="half_open"})""";

    private static final double MILISSEGUNDOS_POR_SEGUNDO = 1_000d;

    private final ClientePrometheus prometheus;

    public ColetorDeStatus(ClientePrometheus prometheus) {
        this.prometheus = prometheus;
    }

    public Mono<StatusDaPlataforma> coletar() {
        return Mono.zip(
                        prometheus.consultar(NO_AR, "job"),
                        prometheus.consultar(LATENCIA_MEDIA, "job"),
                        prometheus.consultar(UPTIME, "job"),
                        prometheus.consultar(ESTADO_DOS_CIRCUITOS, "name"))
                .map(this::montar);
    }

    private StatusDaPlataforma montar(
            Tuple4<Map<String, Double>, Map<String, Double>, Map<String, Double>,
                    Map<String, Double>> respostas) {

        Map<String, Double> noAr = respostas.getT1();
        Map<String, Double> latencia = respostas.getT2();
        Map<String, Double> uptime = respostas.getT3();
        Map<String, Double> circuitos = respostas.getT4();

        // TreeSet para a ordem ser sempre a mesma. Sem isso os cartoes trocariam de lugar entre
        // duas coletas, e a pagina se atualiza a cada poucos segundos — uma tela que embaralha
        // sozinha e mais dificil de ler do que uma com informacao a menos.
        Set<String> nomes = new TreeSet<>(noAr.keySet());

        List<StatusDaPlataforma.Servico> servicos = nomes.stream()
                .map(nome -> new StatusDaPlataforma.Servico(
                        nome,
                        noAr.getOrDefault(nome, 0d) >= 1d,
                        emMilissegundos(latencia.get(nome)),
                        arredondar(uptime.get(nome))))
                .toList();

        List<StatusDaPlataforma.Circuito> estados = circuitos.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entrada -> new StatusDaPlataforma.Circuito(
                        entrada.getKey(), estadoDe(entrada.getValue())))
                .toList();

        return new StatusDaPlataforma(Instant.now(), servicos, estados);
    }

    private Double emMilissegundos(Double segundos) {
        return segundos == null ? null : segundos * MILISSEGUNDOS_POR_SEGUNDO;
    }

    private Long arredondar(Double valor) {
        return valor == null ? null : valor.longValue();
    }

    /**
     * Compara por faixa, e nao por igualdade exata.
     *
     * <p>O valor chega como ponto flutuante depois de uma soma e uma multiplicacao no Prometheus.
     * Um {@code == 2.0} funcionaria hoje e falharia no dia em que a aritmetica devolvesse
     * 1.9999999999999998 — e o modo de falhar seria a tela mostrar "fechado" com o circuito aberto,
     * que e exatamente o erro mais caro que esta pagina pode cometer.
     */
    private StatusDaPlataforma.Estado estadoDe(double valor) {
        if (valor >= 1.5d) {
            return StatusDaPlataforma.Estado.ABERTO;
        }
        if (valor >= 0.5d) {
            return StatusDaPlataforma.Estado.MEIO_ABERTO;
        }
        return StatusDaPlataforma.Estado.FECHADO;
    }
}
