-- Contexto de trace da requisicao que originou o evento.
--
-- A outbox e, por construcao, uma quebra na linha do tempo: a mensagem e gravada dentro da
-- transacao da reserva e publicada segundos depois, por um job, numa thread que nao sabe nada
-- daquela requisicao. Sem esta coluna, o span do consumidor comeca uma arvore nova — e o Jaeger
-- mostra "alguem notificou alguem" sem nenhuma ligacao com a compra que causou a notificacao,
-- que e justamente a pergunta que se quer responder.
--
-- Guardar o traceparent junto da mensagem e o que permite ao publicador restaurar o contexto e
-- pendurar a publicacao na arvore certa.
--
-- Formato W3C Trace Context: `00-<32 hex>-<16 hex>-<2 hex>` = 55 caracteres. O campo e mais
-- folgado para acomodar uma versao futura do formato sem exigir migracao.
ALTER TABLE outbox_messages
    ADD COLUMN trace_parent VARCHAR(64);

-- Nulo e um estado legitimo, e nao uma pendencia a preencher: as mensagens gravadas antes desta
-- migracao nao tem contexto, e um evento registrado por um job — sem requisicao HTTP na origem —
-- tambem nao tera. O publicador trata o nulo publicando normalmente, sem contexto restaurado.
COMMENT ON COLUMN outbox_messages.trace_parent IS
    'traceparent W3C da requisicao de origem; nulo quando nao houve trace ativo';
