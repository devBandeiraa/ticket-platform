# ADR 0002 — Síncrono onde a resposta depende, assíncrono onde não depende

**Estado:** aceita · **Decidida nas** Fases 5 e 12

## O problema

Cinco serviços precisam conversar. A escolha entre chamada síncrona e mensagem não é de gosto:
cada uma falha de um jeito, e o jeito certo depende de quem precisa da resposta.

Duas conversas concretas:

1. O `booking-service` precisa saber a planta e o preço de um evento para aceitar uma reserva.
2. O `notification-service` precisa saber que uma reserva foi paga, para avisar a compradora.

## A decisão

**São opostas, e de propósito.**

### Buscar o evento é síncrono

Sem a planta da casa não há reserva a fazer. A resposta ao usuário **depende** desse dado, então
não há o que desacoplar: uma mensagem só adiaria a mesma espera, com a diferença de que o
usuário ficaria olhando uma tela sem saber se comprou.

A chamada é `RestClient` direto, com circuit breaker. E acontece **uma vez por evento**: na
hidratação do estoque local. Depois disso, as reservas daquele evento não dependem mais de o
`event-service` estar no ar — o que é a mesma ideia do ADR 0001 aplicada entre serviços.

Usa o endpoint público `GET /events/{id}`, que já devolve o que se precisa e já filtra por
publicados — regra desejada, porque não se vende ingresso de rascunho. Um endpoint dedicado
entregaria os mesmos dados sob outra URL, com mais superfície para manter em sincronia.

### Avisar que foi pago é assíncrono

A compradora não precisa esperar o e-mail sair para saber que comprou. Se o
`notification-service` estiver fora do ar, a compra **não pode falhar** — o ingresso é dela, e
o aviso é consequência.

Aqui a mensagem é a escolha certa, e traz o problema clássico junto.

## O problema que a mensagem traz

Publicar no RabbitMQ dentro da transação que confirma o pagamento não resolve nada: são dois
sistemas, e não há transação que cubra os dois.

- **Publicar antes do commit** e o commit falhar → notificação de um pagamento que não existe.
- **Publicar depois do commit** e a publicação falhar → reserva paga que ninguém soube.

A saída é a **outbox transacional**. O evento é gravado numa tabela do mesmo banco, na mesma
transação que confirma a reserva. Os dois fatos existem ou não existem — não há terceiro caso.

Um job varre a tabela e publica. Se a publicação falhar, a linha continua lá e a próxima
varredura tenta de novo. O `notification-service` pode ficar horas fora e nada se perde.

### Um detalhe que custou pensar

O contexto de trace é capturado **no registro**, e não na publicação. No registro ainda se está
dentro da requisição que pagou; na publicação, segundos depois, aquele contexto já não existe.
Capturado lá, a árvore no Jaeger mostraria o job, e não a compra que o originou.

## Consequências

**A favor.** Cada conversa falha do jeito que faz sentido para ela. O caminho da compra sobrevive
ao `notification-service` cair; a reserva sobrevive ao `event-service` cair depois da hidratação.

**Contra.** A outbox é uma peça a mais: tabela, job, monitoramento do atraso. E entrega
**pelo menos uma vez**, não exatamente uma — o consumidor precisa ser idempotente, e é.

**O limite conhecido.** O job varre em intervalo fixo, então há latência entre pagar e notificar.
Para e-mail isso é irrelevante. Para algo que a pessoa espera ver na tela, não serviria — e aí a
resposta seria síncrona, que é justamente a regra deste ADR.

## Onde olhar

- `booking-service/.../client/EventClient.java` — o síncrono, com circuit breaker
- `booking-service/.../messaging/OutboxRegistrar.java` — grava na mesma transação
- `booking-service/.../messaging/OutboxPublisher.java` — o job que publica
- `booking-service/.../integration/OutboxIntegrationTest.java` e `TraceNaOutboxIntegrationTest.java`
- Riscos #11, #26 e #44 em [`00-mapeamento.md`](../00-mapeamento.md)
