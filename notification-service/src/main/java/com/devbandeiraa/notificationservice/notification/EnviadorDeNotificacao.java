package com.devbandeiraa.notificationservice.notification;

import com.devbandeiraa.notificationservice.messaging.BookingConfirmedEvent;

/**
 * Entrega a notificacao ao usuario.
 *
 * <p>E uma interface com uma unica implementacao, e isso costuma ser sinal de abstracao
 * prematura. Aqui nao e: ela marca a fronteira exata onde um provedor real de e-mail entraria, e
 * separa o que o servico decide — <em>quando</em> notificar, o que ja envolve fila, retentativa,
 * deduplicacao e DLQ — de <em>como</em> a mensagem sai, que e detalhe de integracao.
 *
 * <p>A implementacao atual registra a notificacao em log estruturado, sem enviar nada. Trocar por
 * SendGrid ou SES seria escrever outra classe e nao tocar em mais nada deste servico.
 *
 * <p><strong>O que faltaria para valer.</strong> O evento carrega apenas o {@code userId}: e-mail
 * e nome pertencem ao auth-service, e copia-los para dentro de um evento do booking-service o
 * tornaria fonte de uma informacao que nao possui — quando o usuario trocasse de e-mail, o evento
 * seguiria carregando o antigo. Um provedor real precisaria resolver o contato a partir do
 * {@code userId}, e a forma de fazer isso e uma decisao de arquitetura por si: consulta sincrona
 * ao auth-service, que o transformaria em dependencia da notificacao, ou uma replica local
 * alimentada por eventos de usuario, que exigiria estado neste servico. Nenhuma das duas se
 * justifica enquanto a notificacao e um log.
 */
public interface EnviadorDeNotificacao {

    void confirmacaoDeReserva(BookingConfirmedEvent evento);
}
