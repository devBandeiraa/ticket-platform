# ADR 0001 — A garantia contra overselling mora no banco, não no lock

**Estado:** aceita · **Decidida na** Fase 3, endurecida na Fase 17

## O problema

Mil pessoas clicam comprar no mesmo segundo e restam cinquenta ingressos. Quantos você vende?

A resposta ingênua — consultar o estoque, decidir, gravar — vende mais do que existe. Entre a
consulta e a gravação há uma janela, e sob concorrência ela é suficiente para duas requisições
lerem o mesmo "resta 1" e ambas gravarem.

Isso não é um caso raro que aparece em produção depois de meses. É o comportamento normal do
sistema assim que duas pessoas compram ao mesmo tempo.

## As opções

**Lock pessimista sobre o evento.** Serializar as reservas de um mesmo evento, cada compradora
esperando a anterior. Correto, e caro: numa abertura de vendas, as mil pessoas viram uma fila de
mil, e a última espera mil vezes o tempo de uma transação.

**Lock otimista com versão.** Uma coluna `version` na linha do estoque, incrementada a cada
escrita; quem gravar com a versão velha recebe erro e tenta de novo. Funciona, e transforma
disputa em retentativa: sob alta contenção, a maioria das tentativas falha e volta, e o trabalho
útil por requisição despenca.

**Lock distribuído no Redis.** Um `SET NX` por evento antes de decidir. Reduz a contenção no
banco, e tem um problema que nenhum ajuste resolve: **o Redis pode cair**. Um lock que é a
garantia transforma a queda de um cache em venda duplicada.

**Condição dentro do `UPDATE`.** Não separar a decisão da gravação: escrever a condição no
`WHERE` da mesma instrução que grava, e ler quantas linhas foram afetadas.

## A decisão

A garantia é a quarta opção. O lock do Redis existe, mas como **otimização**, nunca como a
correção.

```sql
UPDATE event_seats
   SET status = 'RESERVED', booking_id = :bookingId
 WHERE id IN (:assentos)
   AND event_id = :eventId
   AND status = 'FREE'
```

Linhas afetadas diferentes de lugares pedidos significa que algum assento saiu no instante exato
do `UPDATE` — não antes dele, que é o que torna a verificação confiável. A transação desfaz, e o
cliente recebe `409` **nomeando os lugares perdidos**, que é o que a tela precisa para remarcar
o mapa.

O PostgreSQL avalia o `WHERE` sob o lock de linha que ele próprio adquire para atualizar. Não há
janela entre decidir e gravar porque **não há decisão separada da gravação**.

### O que a Fase 17 endureceu

Antes, o estoque era um contador com `CHECK (reserved <= total)`. A garantia dependia de o
contador estar certo.

Hoje existe **uma linha por lugar**, e ela só sai de `FREE` uma vez. Não há contador que possa
passar do teto — precisaria existir duas linhas para o mesmo assento, e a unicidade da chave
natural (evento, setor, fila, número) não permite. A garantia deixou de ser uma regra aplicada
sobre o dado e passou a ser uma propriedade da forma do dado.

## Como isto é demonstrado

Uma afirmação dessas só vale demonstrada. Há dois testes:

`OversellingConcorrenteIntegrationTest` — 200 compradores simultâneos contra 50 lugares, com o
lock ligado. Vendem-se exatamente 50.

`OversellingSemLockIntegrationTest` — **a mesma disputa, com o lock substituído por uma
implementação que só executa a operação**, reproduzindo o que acontece com o Redis fora do ar.
As 200 threads chegam ao banco sem serialização nenhuma. Vendem-se exatamente 50.

O segundo é o teste que sustenta a tese do projeto. Se ele falhar, tudo aqui está apoiado numa
premissa falsa.

## Consequências

**A favor.** A correção não depende de nenhuma infraestrutura além do banco que já é
obrigatório. O Redis pode cair e o sistema degrada em latência, não em correção. A concorrência
é resolvida onde ela realmente acontece.

**Contra.** A contenção vai para o banco. Sob carga muito alta, o custo aparece como espera de
lock de linha no PostgreSQL, e a saída seria particionar por evento — o que este projeto não
faz e não precisa.

**Assimetria deliberada.** Liberar um lugar — no cancelamento e na expiração — dispensa o lock.
Liberar nunca cria conflito: no máximo dois caminhos tentam liberar o mesmo, e o segundo afeta
zero linhas. Só a tomada disputa.

## Onde olhar

- `booking-service/.../repository/EventSeatRepository.java` — o `UPDATE` condicional
- `booking-service/.../integration/OversellingSemLockIntegrationTest.java` — a prova
- `booking-service/.../db/migration/V6__reserva_por_assento.sql` — a linha por lugar
- Riscos #2, #7 e #40 em [`00-mapeamento.md`](../00-mapeamento.md)
