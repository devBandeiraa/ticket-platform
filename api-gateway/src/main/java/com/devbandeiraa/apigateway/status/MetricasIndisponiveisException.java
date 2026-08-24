package com.devbandeiraa.apigateway.status;

/**
 * O Prometheus nao respondeu.
 *
 * <p>Existe para o painel poder dizer <em>"nao sei"</em> em vez de "tudo fora". Sao coisas
 * opostas: sem esta distincao, uma queda do proprio Prometheus pintaria a plataforma inteira de
 * vermelho e mandaria alguem investigar seis servicos que estao perfeitamente no ar.
 */
public class MetricasIndisponiveisException extends RuntimeException {

    public MetricasIndisponiveisException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
