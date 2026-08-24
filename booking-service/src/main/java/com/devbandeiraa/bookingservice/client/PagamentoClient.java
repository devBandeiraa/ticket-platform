package com.devbandeiraa.bookingservice.client;

import com.devbandeiraa.bookingservice.exception.PagamentoIndisponivelException;
import com.devbandeiraa.bookingservice.exception.PagamentoRecusadoException;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cobra do provedor de pagamento externo.
 *
 * <h2>Por que retry aqui, e nao no event-service</h2>
 *
 * <p>Sao dois vizinhos com naturezas diferentes. O event-service e nosso, roda ao lado e responde
 * rapido: quando ele falha, e provavel que va continuar falhando, e insistir so empilha
 * requisicoes em cima de um servico que ja esta mal — dai circuit breaker la. O provedor de
 * pagamento e um terceiro atras da internet, onde falha isolada e transitoria e o caso comum:
 * um pacote perdido, um pico de latencia. Desistir na primeira tentativa perderia uma venda que
 * teria acontecido.
 *
 * <h2>O que torna o retry seguro</h2>
 *
 * <p>Repetir uma cobranca e perigoso por natureza: depois de um timeout, nao se sabe se ela passou
 * do outro lado. Quem torna isso seguro nao e a configuracao do retry — e a chave de idempotencia
 * derivada do id da reserva, sempre a mesma para as quatro tentativas. O provedor reconhece a
 * chave e devolve a cobranca ja feita em vez de cobrar de novo.
 *
 * <p>Sem idempotencia, este retry seria um gerador de cobranca em duplicidade com aparencia de
 * boa pratica.
 */
@Component
public class PagamentoClient {

    private static final Logger log = LoggerFactory.getLogger(PagamentoClient.class);

    /** Nome da instancia configurada em {@code resilience4j.retry.instances} no application.yml. */
    public static final String INSTANCIA = "pagamento";

    private final RestClient restClient;

    public PagamentoClient(RestClient pagamentoRestClient) {
        this.restClient = pagamentoRestClient;
    }

    /**
     * Autoriza a cobranca da reserva.
     *
     * <p>A anotacao repete apenas {@link PagamentoIndisponivelException}; a recusa esta na lista de
     * excecoes ignoradas do application.yml e sobe na primeira tentativa.
     *
     * @throws PagamentoRecusadoException    se o provedor avaliou e negou a cobranca
     * @throws PagamentoIndisponivelException se as tentativas se esgotaram sem resposta
     */
    @Retry(name = INSTANCIA)
    public Autorizacao autorizar(UUID reservaId, BigDecimal valor) {
        try {
            Autorizacao autorizacao = restClient.post()
                    .uri("/payments")
                    // A chave nasce do id da reserva, e nao de um sorteio: precisa ser identica
                    // nas quatro tentativas, e tambem identica se o usuario clicar em "pagar" de
                    // novo depois de a tela ter travado. Uma reserva, uma cobranca.
                    .header("Idempotency-Key", chaveDe(reservaId))
                    .body(Map.of("bookingId", reservaId, "amount", valor))
                    .retrieve()
                    .body(Autorizacao.class);

            if (autorizacao == null) {
                throw new PagamentoIndisponivelException(
                        reservaId, "o provedor devolveu um corpo vazio", null);
            }

            if (autorizacao.repetida()) {
                log.info("cobranca da reserva {} ja havia sido feita: comprovante {}",
                        reservaId, autorizacao.authorizationCode());
            }

            return autorizacao;

        } catch (HttpClientErrorException erroDoCliente) {
            // Comparacao explicita do status porque o Spring nao tem uma subclasse dedicada ao
            // 402 — ele so as oferece para um conjunto fixo, e este nao esta nele.
            if (erroDoCliente.getStatusCode() == HttpStatus.PAYMENT_REQUIRED) {
                // 402 e o unico 4xx que o provedor usa para dizer "avaliei e neguei". Traduzido
                // para excecao propria porque, do ponto de vista do retry, ele e o oposto de uma
                // falha: e uma resposta, e uma resposta nao se repete.
                throw new PagamentoRecusadoException(reservaId, erroDoCliente.getMessage());
            }

            // Demais 4xx sao defeito nosso — cabecalho faltando, corpo malformado. Repetir nao
            // conserta, e transformar isso em falha transitoria esconderia o defeito atras de
            // quatro tentativas e um erro final que aponta para o provedor.
            log.error("requisicao invalida ao provedor de pagamento na reserva {}: {}",
                    reservaId, erroDoCliente.getMessage());
            throw new PagamentoRecusadoException(
                    reservaId, "requisicao recusada pelo provedor: " + erroDoCliente.getMessage());

        } catch (RestClientException falhaDeComunicacao) {
            // Timeout, conexao recusada, 5xx. O caso que o retry existe para atender.
            throw new PagamentoIndisponivelException(
                    reservaId, falhaDeComunicacao.getMessage(), falhaDeComunicacao);
        }
    }

    /**
     * Cancela uma cobranca ja autorizada.
     *
     * <p>Chamado quando a reserva expirou entre a cobranca e a confirmacao. Nao leva retry de
     * proposito: e uma compensacao no caminho de erro, e prender a resposta do usuario por mais
     * quatro tentativas nao mudaria o que ele ve. Falhando, o comprovante fica registrado no log
     * como pendencia — que e mais honesto que fingir que o estorno sempre funciona.
     *
     * @return {@code true} se o provedor confirmou o estorno
     */
    public boolean estornar(UUID reservaId, String comprovante) {
        try {
            restClient.post()
                    .uri("/payments/{comprovante}/void", comprovante)
                    .retrieve()
                    .toBodilessEntity();

            log.info("cobranca da reserva {} estornada: comprovante {}", reservaId, comprovante);
            return true;

        } catch (RestClientException falha) {
            log.error("ESTORNO PENDENTE: reserva={} comprovante={} — a cobranca passou, a reserva "
                            + "nao foi confirmada e o estorno falhou. Exige verificacao manual.",
                    reservaId, comprovante, falha);
            return false;
        }
    }

    private String chaveDe(UUID reservaId) {
        return "booking-" + reservaId;
    }
}
