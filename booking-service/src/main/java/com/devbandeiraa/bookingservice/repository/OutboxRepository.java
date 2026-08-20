package com.devbandeiraa.bookingservice.repository;

import com.devbandeiraa.bookingservice.domain.OutboxMessage;
import com.devbandeiraa.bookingservice.domain.OutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acesso a outbox.
 *
 * <p>Como nas reservas, as mudancas de estado sao {@code UPDATE} condicional. Aqui isso importa
 * porque, com varias replicas do servico, todas varrem a outbox ao mesmo tempo: e o
 * {@code WHERE status = PENDING} que garante que apenas uma consiga marcar cada mensagem, e
 * portanto que apenas uma a publique de fato.
 *
 * <p>Diferente dos repositorios de reserva e estoque, os metodos de escrita daqui sao
 * {@code @Transactional} por conta propria. La as operacoes precisam compartilhar a transacao de
 * quem chama — tomar estoque e gravar a reserva tem de ser um so ato. Aqui cada marcacao e
 * independente das demais: o publicador processa uma mensagem por vez, e uma falha na terceira
 * nao deve desfazer o registro das duas ja entregues ao broker, que nao voltam.
 */
public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    /** Pendentes mais antigas primeiro: a ordem de publicacao acompanha a ordem dos fatos. */
    @Query("""
            SELECT mensagem
              FROM OutboxMessage mensagem
             WHERE mensagem.status = :pendente
             ORDER BY mensagem.createdAt ASC
            """)
    List<OutboxMessage> buscarPendentes(@Param("pendente") OutboxStatus pendente, Limit limite);

    /**
     * Marca como publicada.
     *
     * @return 1 se esta chamada foi a que marcou, 0 se outra replica chegou antes
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OutboxMessage mensagem
               SET mensagem.status = :publicada,
                   mensagem.publishedAt = :agora,
                   mensagem.attempts = mensagem.attempts + 1
             WHERE mensagem.id = :id
               AND mensagem.status = :pendente
            """)
    int marcarComoPublicada(@Param("id") UUID id,
                            @Param("agora") Instant agora,
                            @Param("pendente") OutboxStatus pendente,
                            @Param("publicada") OutboxStatus publicada);

    /**
     * Registra uma tentativa fracassada, mantendo a mensagem pendente para a proxima varredura.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OutboxMessage mensagem
               SET mensagem.attempts = mensagem.attempts + 1,
                   mensagem.lastError = :erro
             WHERE mensagem.id = :id
               AND mensagem.status = :pendente
            """)
    int registrarFalha(@Param("id") UUID id,
                       @Param("erro") String erro,
                       @Param("pendente") OutboxStatus pendente);

    /** Desiste da mensagem, tirando-a do caminho das demais. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OutboxMessage mensagem
               SET mensagem.status = :falhou,
                   mensagem.attempts = mensagem.attempts + 1,
                   mensagem.lastError = :erro
             WHERE mensagem.id = :id
               AND mensagem.status = :pendente
            """)
    int desistir(@Param("id") UUID id,
                 @Param("erro") String erro,
                 @Param("pendente") OutboxStatus pendente,
                 @Param("falhou") OutboxStatus falhou);

    // Os metodos abaixo apenas fixam as constantes de estado das consultas acima.

    default List<OutboxMessage> proximoLotePendente(int tamanhoDoLote) {
        return buscarPendentes(OutboxStatus.PENDING, Limit.of(tamanhoDoLote));
    }

    default int marcarComoPublicada(UUID id, Instant agora) {
        return marcarComoPublicada(id, agora, OutboxStatus.PENDING, OutboxStatus.PUBLISHED);
    }

    default int registrarFalha(UUID id, String erro) {
        return registrarFalha(id, erro, OutboxStatus.PENDING);
    }

    default int desistir(UUID id, String erro) {
        return desistir(id, erro, OutboxStatus.PENDING, OutboxStatus.FAILED);
    }
}
