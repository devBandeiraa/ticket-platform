# ticket-platform

Plataforma de venda de ingressos em microsserviços. Java 17, Spring Boot 3.5, PostgreSQL, Redis,
RabbitMQ e um frontend em React.

O objetivo não é demonstrar CRUDs isolados. É resolver um problema real de concorrência
distribuída: **impedir overselling de ingressos sob carga simultânea** — e mostrar onde,
exatamente, essa garantia mora.

```bash
git clone https://github.com/devBandeiraa/ticket-platform
cd ticket-platform
docker compose up --build
```

Depois disso, http://localhost:5173. Entre com `admin@ticket.dev` / `admin@ticket.dev123`.

Nenhum `.env` é necessário — todo valor tem padrão. Copie `.env.example` só se algum deles não
servir, tipicamente uma porta já ocupada.

## A tese

**A garantia contra overselling não vem do lock distribuído.** O lock existe, no Redis, e reduz
contenção — mas é otimização. Se ele expirar no meio da transação, ou se o Redis cair, a correção
não se perde.

Ela mora no PostgreSQL, num `UPDATE` condicional protegido por uma `CHECK constraint`:

```sql
UPDATE event_inventory
   SET reserved_tickets = reserved_tickets + :quantidade
 WHERE event_id = :eventId
   AND reserved_tickets + :quantidade <= total_tickets
```

Zero linhas afetadas significa que os ingressos acabaram entre a consulta e a escrita. O
`booking-service` responde `409 SOLD_OUT`, e não há janela entre verificar e gravar, porque as
duas coisas são a mesma operação.

Dá para ver isso acontecer em **http://localhost:5173/demo/concorrencia**: escolha um evento,
dispare mais reservas simultâneas do que há ingressos, e confira a linha *vendidos a mais*.

> Este projeto já vendeu 70 ingressos para um evento de 50. O teste de concorrência pegou na
> primeira execução, e a causa foi um `save()` do Spring Data virando `merge` numa entidade de id
> atribuído. Está registrado como risco #16 no mapeamento — junto do que se fez a respeito.

## Arquitetura

```
                            ┌──────────────┐
                            │   Frontend   │  React + TS + Tailwind  :5173
                            └──────┬───────┘
                                   │ /api/**
                            ┌──────▼───────┐
                            │ api-gateway  │  :8080
                            │ valida JWT   │  rate limit (Redis)
                            └──┬────┬────┬─┘
                  ┌────────────┘    │    └────────────┐
                  ▼                 ▼                 ▼
          ┌──────────────┐  ┌──────────────┐  ┌───────────────┐
          │ auth-service │  │event-service │  │booking-service│
          │    :8081     │  │    :8082     │  │    :8083      │
          └──────┬───────┘  └──────┬───────┘  └──┬─────────┬──┘
                 │                 │             │         │
             ┌───▼───┐         ┌───▼───┐     ┌───▼───┐ ┌───▼──┐
             │authdb │         │eventdb│     │booking│ │Redis │
             └───────┘         └───────┘     │  db   │ │ lock │
                                             └───────┘ └──────┘
                                                 │ outbox
                                          ┌──────▼──────┐
                                          │  RabbitMQ   │
                                          └──────┬──────┘
                                                 ▼
                                     ┌───────────────────────┐
                                     │ notification-service  │ :8084
                                     │      (sem banco)      │
                                     └───────────────────────┘
```

| Serviço | Porta | Banco | Papel |
|---|---|---|---|
| `api-gateway` | 8080 | — | Roteamento, autenticação na borda, rate limiting |
| `auth-service` | 8081 | `authdb` | Cadastro, login, emissão de JWT |
| `event-service` | 8082 | `eventdb` | Catálogo e gestão de eventos |
| `booking-service` | 8083 | `bookingdb` | Reserva com lock distribuído — núcleo do projeto |
| `notification-service` | 8084 | — | Consumidor de fila, notificação assíncrona |
| `frontend` | 5173 | — | Interface, consome só o gateway |

`shared-security` é um módulo de biblioteca, não um serviço: validação de JWT e formato de erro,
compartilhados sem que nenhum serviço dependa de outro em tempo de execução.

## Além do overselling

- **Transactional outbox** — o evento de confirmação é gravado na mesma transação que confirma a
  reserva. Publicar direto no RabbitMQ deixaria a reserva `CONFIRMED` sem que ninguém fosse
  avisado, em silêncio.
- **Idempotência em duas pontas** — `Idempotency-Key` com unique constraint na criação da reserva;
  deduplicação por `message-id` no Redis do lado do consumidor, já que a outbox entrega *ao menos
  uma vez*.
- **Degradação deliberada** — com o Redis fora do ar a reserva continua, sem lock, correta pelo
  `UPDATE` condicional. Recusar faria do cache um ponto único de falha da operação mais importante
  do sistema.
- **Autentica na borda, autoriza no serviço** — o gateway confere *quem* está chamando e apaga
  qualquer `X-User-*` que o cliente tenha enviado. *O que* cada um pode fazer continua em cada
  serviço, que segue validando o JWT: é o que impede que alcançar um serviço por dentro da rede
  contorne a autenticação.
- **Dead-letter queue** — 3 tentativas com backoff e então DLQ. Payload malformado vai direto,
  sem retentar: retentar não conserta JSON quebrado.

## Testes

**182 no backend**, com PostgreSQL, Redis e RabbitMQ reais via Testcontainers, e **18 no
frontend**.

Os que mais importam:

| Teste | O que prova |
|---|---|
| `OversellingConcorrenteIntegrationTest` | 200 threads, 50 ingressos, exatamente 50 vendidos |
| `OversellingSemLockIntegrationTest` | O mesmo **sem o lock** — a garantia é do banco |
| `OutboxIntegrationTest` | O evento sobrevive à falha do publicador |
| `ConsumoDeConfirmacaoIntegrationTest` | Duplicata descartada, retry, DLQ |
| `RateLimitIntegrationTest` | Os dois baldes, e o `429` no formato de erro da API |
| `cliente.test.ts` | Renovação de token compartilhada entre chamadas simultâneas |

```bash
./mvnw clean install          # backend (exige Docker, para os Testcontainers)
cd frontend && npm test       # frontend
```

## Rodando sem containers

Para depurar um serviço pela IDE, suba só a infraestrutura:

```bash
docker compose up postgres redis rabbitmq
./mvnw clean install
SPRING_PROFILES_ACTIVE=dev java -jar auth-service/target/auth-service-0.0.1-SNAPSHOT.jar
# ... e os demais
cd frontend && npm run dev
```

O profile `dev` no `auth-service` aplica o seed do primeiro administrador. Sem ele o banco nasce
sem admin, e não há caminho para criar o primeiro evento — o cadastro público sempre gera `USER`.

## Documentação

[`docs/00-mapeamento.md`](docs/00-mapeamento.md) tem o modelo de dados, os contratos de API, o
fluxo da reserva passo a passo, **28 riscos técnicos** com o que se fez sobre cada um e a tabela
de decisões — cada escolha com a justificativa e a alternativa recusada.

Foi escrito antes da primeira linha de código de negócio, e atualizado a cada fase.
