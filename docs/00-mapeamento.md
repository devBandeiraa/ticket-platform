# Mapeamento de Arquitetura

Documento da Fase 0 do projeto: modelo de dados, contratos de API, fluxo da reserva,
estrutura de pastas e riscos técnicos. Escrito **antes** de qualquer linha de código de
negócio, para que as decisões estruturais fossem tomadas de forma explícita.

O objetivo do projeto não é demonstrar CRUDs isolados, e sim resolver um problema real de
concorrência distribuída: **impedir overselling de ingressos sob carga simultânea**.

---

## Visão geral

```
                            ┌──────────────┐
                            │   Frontend   │  React + TS + Tailwind
                            └──────┬───────┘
                                   │ /api/**
                            ┌──────▼───────┐
                            │ api-gateway  │  :8080
                            │ valida JWT   │  rate limit (Redis)
                            └──┬────┬────┬─┘
                  ┌────────────┘    │    └────────────┐
                  ▼                 ▼                 ▼
          ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
          │ auth-service │  │event-service │  │booking-service│
          │    :8081     │  │    :8082     │  │    :8083     │
          └──────┬───────┘  └──────┬───────┘  └──┬────────┬──┘
                 │                 │             │        │
             ┌───▼───┐         ┌───▼───┐     ┌───▼───┐ ┌──▼───┐
             │authdb │         │eventdb│     │booking│ │Redis │
             └───────┘         └───────┘     │  db   │ │ lock │
                                             └───────┘ └──────┘
                                                 │
                                          ┌──────▼──────┐
                                          │  RabbitMQ   │
                                          └──────┬──────┘
                                                 ▼
                                     ┌───────────────────────┐
                                     │ notification-service  │ :8084
                                     │   (sem banco)         │
                                     └───────────────────────┘
```

| Serviço | Porta | Banco | Papel |
|---|---|---|---|
| `api-gateway` | 8080 | — | Roteamento, validação de JWT, rate limiting |
| `auth-service` | 8081 | `authdb` | Cadastro, login, emissão de JWT |
| `event-service` | 8082 | `eventdb` | Catálogo e gestão de eventos |
| `booking-service` | 8083 | `bookingdb` | Reserva com lock distribuído — núcleo do projeto |
| `notification-service` | 8084 | — | Consumidor de fila, notificação assíncrona |

---

## 1. Modelo de dados

Regra transversal: cada serviço tem seu próprio banco e **nenhuma chave estrangeira atravessa
serviço**. Referências entre serviços são `UUID` "solto", resolvido por chamada de API ou
mensagem.

### `auth-service` → `authdb`

**`users`**

| Campo | Tipo | Observação |
|---|---|---|
| `id` | UUID PK | gerado na aplicação |
| `email` | varchar(255) **unique** | identificador de login |
| `password_hash` | varchar(60) | BCrypt |
| `full_name` | varchar(120) | |
| `role` | enum `USER` \| `ADMIN` | persistido como string |
| `enabled` | boolean | default `true` |
| `created_at` / `updated_at` | timestamptz | |

**`refresh_tokens`**

| Campo | Tipo | Observação |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID FK → `users` | única FK real do projeto, por ser intra-serviço |
| `token_hash` | varchar | guarda o hash, nunca o token em claro |
| `expires_at` | timestamptz | |
| `revoked` | boolean | permite logout efetivo |

### `event-service` → `eventdb`

**`events`**

| Campo | Tipo | Observação |
|---|---|---|
| `id` | UUID PK | |
| `name` | varchar(150) | |
| `description` | text | |
| `venue` | varchar(200) | local do evento |
| `event_date` | timestamptz | |
| `total_tickets` | int | capacidade — **autoritativa aqui** |
| `price` | numeric(10,2) | |
| `status` | enum `DRAFT` \| `PUBLISHED` \| `CANCELLED` | |
| `created_by` | UUID | id do admin, **sem FK** |
| `created_at` / `updated_at` | timestamptz | |

Índice `(status, event_date)` para a listagem pública paginada.

O `event-service` **não** registra quantos ingressos foram vendidos — ver decisão em
[3. Fluxo da reserva](#3-fluxo-da-reserva).

### `booking-service` → `bookingdb`

**`event_inventory`** — réplica local do estoque, autoritativa para a decisão de reserva

| Campo | Tipo | Observação |
|---|---|---|
| `event_id` | UUID PK | mesmo id usado no `event-service` |
| `total_tickets` | int | copiado do `event-service` |
| `reserved_tickets` | int | default 0 |
| `version` | int | `@Version`, lock otimista |
| `synced_at` | timestamptz | |

```sql
CHECK (reserved_tickets >= 0 AND reserved_tickets <= total_tickets)
```

Essa constraint é a **última rede de segurança** contra overselling: mesmo que toda a lógica
de aplicação falhe, o banco recusa o estado inválido.

**`bookings`**

| Campo | Tipo | Observação |
|---|---|---|
| `id` | UUID PK | |
| `event_id` | UUID | sem FK |
| `user_id` | UUID | sem FK, extraído do JWT |
| `quantity` | int | `> 0` |
| `unit_price` / `total_price` | numeric(10,2) | **snapshot** do preço no ato da reserva |
| `status` | enum `PENDING` \| `CONFIRMED` \| `CANCELLED` \| `EXPIRED` | |
| `expires_at` | timestamptz | preenchido apenas enquanto `PENDING` |
| `paid_at` | timestamptz | null até o pagamento simulado |
| `idempotency_key` | varchar **unique** | impede reserva duplicada em retry |
| `created_at` / `updated_at` | timestamptz | |

Índices: `(user_id, created_at)`, `(event_id, status)`.

O preço é copiado, não referenciado: se o admin alterar o valor do evento amanhã, a reserva
de hoje precisa continuar valendo o que valia hoje.

**Ciclo de vida da reserva**

```
              POST /bookings                POST /bookings/{id}/pay
   (nada) ─────────────────▶ PENDING ──────────────────────────────▶ CONFIRMED
                             │  ▲                                     (paid_at)
              job a cada 1min│  │ segura o estoque
         expires_at < now()  │  │ desde já
                             ▼  │
                          EXPIRED│         POST /bookings/{id}/cancel
                                 └──────────────────────────────────▶ CANCELLED

                    EXPIRED e CANCELLED devolvem estoque ao event_inventory
```

### `notification-service` → sem banco

Consumidor stateless. Não há estado a persistir para registrar e "enviar" uma notificação.
Vale o registro explícito: *database per service* não significa *database always* — inventar
um banco aqui seria complexidade sem função.

### `api-gateway` → sem banco

Stateless. Usa Redis apenas como backend do rate limiter.

---

## 2. Contratos de API

Todo consumo pelo frontend passa pelo gateway, sob o prefixo `/api`, que aplica `StripPrefix=1`
antes de encaminhar.

Legenda: 🌐 público · 🔒 exige JWT · 🔗 interno (não exposto no gateway)

### `auth-service`

| Método | Path | Auth | Request → Response |
|---|---|---|---|
| POST | `/auth/register` | 🌐 | `{email, password, fullName}` → `201 {id, email, fullName, role}` |
| POST | `/auth/login` | 🌐 | `{email, password}` → `200 {accessToken, refreshToken, tokenType, expiresIn}` |
| POST | `/auth/refresh` | 🌐 | `{refreshToken}` → `200 {accessToken, refreshToken, ...}` |
| POST | `/auth/logout` | 🔒 | `{refreshToken}` → `204` (revoga) |
| GET | `/auth/me` | 🔒 | → `{id, email, fullName, role}` |

### `event-service`

Catálogo público e administração vivem em prefixos separados. A autorização vira uma regra única
de prefixo — `/admin/**` exige `ADMIN` — em vez de uma anotação por método, que depende de alguém
lembrar de colocá-la. Um endpoint administrativo novo já nasce protegido por morar sob o prefixo.

| Método | Path | Auth | Observação |
|---|---|---|---|
| GET | `/events?page=&size=&busca=&de=&ate=` | 🌐 | apenas `PUBLISHED`; ordenado por data; teto de 100 por página |
| GET | `/events/{id}` | 🌐 | apenas `PUBLISHED`; rascunho devolve `404`, não `403` |
| GET | `/admin/events?status=&page=&size=` | 🔒 ADMIN | enxerga rascunhos e cancelados |
| GET | `/admin/events/{id}` | 🔒 ADMIN | qualquer status |
| POST | `/admin/events` | 🔒 ADMIN | nasce `DRAFT`; `201` + header `Location` |
| PUT | `/admin/events/{id}` | 🔒 ADMIN | `409` se cancelado |
| POST | `/admin/events/{id}/publish` | 🔒 ADMIN | `DRAFT` → `PUBLISHED`; idempotente |
| DELETE | `/admin/events/{id}` | 🔒 ADMIN | exclusão lógica → `status = CANCELLED` |
| GET | `/internal/events/{id}` | 🔗 | consumido pelo `booking-service` (Fase 4) |

Um evento **nasce como `DRAFT` e só aparece no catálogo por um ato deliberado de publicação**.
Publicar por acidente, ao salvar um cadastro pela metade, é o tipo de erro que só se percebe
quando alguém já comprou.

### `booking-service`

| Método | Path | Auth | Observação |
|---|---|---|---|
| POST | `/bookings` | 🔒 USER | `{eventId, quantity}` + header `Idempotency-Key`; `201` \| `409 SOLD_OUT` |
| POST | `/bookings/{id}/pay` | 🔒 dono | pagamento simulado; `PENDING` → `CONFIRMED`; `409 BOOKING_EXPIRED` se venceu |
| POST | `/bookings/{id}/cancel` | 🔒 dono | → `CANCELLED`, devolvendo estoque |
| GET | `/bookings/me?page=&size=` | 🔒 USER | reservas do usuário autenticado |
| GET | `/bookings/{id}` | 🔒 dono ou ADMIN | |
| GET | `/events/{eventId}/availability` | 🌐 | `{eventId, total, reserved, available}` |
| GET | `/bookings?eventId=&status=` | 🔒 ADMIN | alimenta o dashboard |

### `notification-service`

Sem API HTTP de negócio — apenas `/actuator/health`. Comunica-se exclusivamente por RabbitMQ.

### Formato de erro

Resposta uniforme em todos os serviços:

```json
{
  "timestamp": "2026-08-19T18:30:00Z",
  "status": 409,
  "error": "SOLD_OUT",
  "message": "Ingressos insuficientes para o evento",
  "path": "/api/bookings",
  "traceId": "a1b2c3d4"
}
```

Códigos de negócio relevantes: `409 SOLD_OUT`, `409 LOCK_TIMEOUT`, `409 BOOKING_EXPIRED`,
`422 EVENT_NOT_PUBLISHED`.

---

## 3. Fluxo da reserva

### Decisão: contador local, não consulta síncrona

O `booking-service` mantém `event_inventory` no próprio banco em vez de perguntar ao
`event-service` a cada reserva.

> A pergunta "ainda cabe mais um ingresso?" precisa ser **atômica com a gravação da reserva**.
> Se o estoque vive no banco do `event-service` e a reserva no do `booking-service`, abre-se
> uma janela entre "consultei" e "gravei" que nenhum lock fecha sem transação distribuída.
> Com o contador no mesmo banco da reserva, um único `UPDATE` condicional resolve a corrida
> com garantia ACID nativa do PostgreSQL, e o `event-service` segue dono do catálogo.

O custo é consistência eventual na capacidade — aceitável, porque capacidade só muda quando um
admin edita o evento. A sincronização usa hidratação preguiçosa via REST na primeira reserva de
cada evento, mais mensagem `event.updated` quando `total_tickets` é alterado.

### Passo a passo

```
┌──────────┐   POST /api/bookings          ┌─────────────┐
│ Frontend │──── JWT + Idempotency-Key ───▶│ api-gateway │
└──────────┘                               └──────┬──────┘
                                    valida JWT │ rate limit
                            injeta X-User-Id / X-User-Role
                                                  ▼
                                        ┌──────────────────┐
                                        │ booking-service  │
                                        └──────────────────┘

   ① Idempotency-Key já existe? ──sim──▶ devolve a reserva existente (200)
                    │ não
                    ▼
   ② event_inventory existe? ──não──▶ GET /internal/events/{id} ──▶ [event-service]
                    │ sim                        (hidrata e persiste)
                    ▼
   ③ REDIS: SET lock:event:{id} {token} NX PX 3000
        └─ falhou? retry com backoff (3x, ~50ms) → esgotou = 409 LOCK_TIMEOUT
                    │ obtido
                    ▼
   ④ POSTGRES, uma transação:
        UPDATE event_inventory
           SET reserved_tickets = reserved_tickets + :qty
         WHERE event_id = :id
           AND reserved_tickets + :qty <= total_tickets    ◀── a garantia real
        ├─ 0 linhas afetadas → rollback → 409 SOLD_OUT
        └─ 1 linha → INSERT INTO bookings (status = PENDING, expires_at = now + TTL)
                    │ commit
                    ▼
   ⑤ REDIS: libera o lock via script Lua
        (só apaga se o valor ainda for o token desta requisição)
                    ▼
   ⑥ 201 Created {id, status: PENDING, expiresAt} ──▶ Frontend inicia contagem regressiva


   ⑦ POST /api/bookings/{id}/pay  (pagamento simulado)
        UPDATE bookings SET status = 'CONFIRMED', paid_at = now()
         WHERE id = :id AND status = 'PENDING' AND expires_at > now()
        ├─ 0 linhas → 409 BOOKING_EXPIRED
        └─ 1 linha → publica booking.confirmed no RabbitMQ
                    │  exchange: booking.exchange (topic)
                    ▼
        ┌──────────────────────┐  consome notification.booking.confirmed
        │ notification-service │  "envia" notificação (log estruturado) + ack
        └──────────────────────┘  falhou N vezes → DLQ
```

### Papel de cada peça

- **Lock do Redis (③ / ⑤)** — serializa tentativas concorrentes **do mesmo evento**. Reduz
  contenção e retrabalho no banco, e torna a seção crítica explícita.
- **`UPDATE` condicional no PostgreSQL (④)** — é a garantia real contra overselling. Ponto
  central do projeto: *o lock distribuído sozinho não basta*. O TTL pode expirar durante uma
  pausa de GC e colocar duas threads na seção crítica ao mesmo tempo. Quem impede o estoque de
  estourar é o banco, aconteça o que acontecer com o Redis.
- **RabbitMQ (⑦)** — tira a notificação do caminho crítico. O usuário recebe a resposta sem
  esperar o "e-mail", e uma falha no `notification-service` não derruba a venda.

### Devolução de estoque não precisa de lock

Os caminhos de expiração e cancelamento **dispensam o lock do Redis**. Decrementar
`reserved_tickets` nunca viola a invariante `reserved <= total` — apenas a reserva pode. E a
devolução dupla é impedida pela transição de status ser um `UPDATE` condicional single-shot:
somente a transação que efetivamente alterou a linha de `PENDING` executa o decremento.

O lock protege exatamente uma direção, e isso é intencional.

---

## 4. Estrutura de pastas

```
src/main/java/com/devbandeiraa/<servico>/
├── <Servico>Application.java
├── config/          # SecurityConfig, RedisConfig, RabbitConfig, OpenApiConfig
├── controller/      # só HTTP: recebe DTO, delega, devolve DTO
├── dto/
│   ├── request/     # records com Bean Validation
│   └── response/    # nunca expõe entidade JPA diretamente
├── domain/          # entidades JPA + enums
├── repository/      # interfaces Spring Data
├── service/         # regra de negócio e orquestração; onde vive a transação
├── client/          # RestClient para outros serviços (só booking-service)
├── messaging/
│   ├── publisher/
│   └── consumer/
├── exception/       # exceções de domínio + @RestControllerAdvice
└── mapper/          # entidade ⇄ DTO

src/test/java/com/devbandeiraa/<servico>/
├── unit/            # service com Mockito, sem contexto Spring
└── integration/     # Testcontainers (Postgres, Redis e RabbitMQ reais)
```

O `controller` não conhece JPA, o `service` não conhece HTTP, o `repository` não conhece regra
de negócio. Só o `booking-service` ganha `client/` e `messaging/`, por ser o único que conversa
com todos os outros.

---

## 5. Riscos técnicos

| # | Risco | Gravidade | Mitigação |
|---|---|---|---|
| 1 | Lock do Redis expira durante a transação (TTL menor que a duração) → duas threads na seção crítica | Alta | `UPDATE` condicional + `CHECK constraint` garantem a correção mesmo assim; o lock é otimização, não a garantia |
| 2 | Liberar o lock de outro processo: se o TTL expirou e outro já o adquiriu, um `DEL` cego mata o lock alheio | Alta | Liberação por script Lua com comparação de token (compare-and-delete) |
| 3 | Redis indisponível → reservas param | Média | Definir política: falhar rápido (`503`) ou degradar para "só banco", que segue correto, apenas mais contencioso |
| 4 | Reserva confirmada mas `publish` no RabbitMQ falha → notificação perdida silenciosamente | Alta | Transactional outbox ou, no mínimo, publisher confirms com log de falha |
| 5 | Deriva de estoque entre serviços (mensagem perdida, ou admin reduz capacidade abaixo do já vendido) | Média | Validar no `event-service` que o novo `total_tickets` ≥ vendidos; endpoint admin de reconciliação |
| 6 | Retry do cliente cria reserva duplicada | Média | `Idempotency-Key` com unique constraint |
| 7 | Teste de concorrência intermitente — H2 não reproduz o isolamento do PostgreSQL | Média | Testcontainers com Postgres e Redis reais; rodar o teste várias vezes no checkpoint |
| 8 | JWT sem revogação: logout não invalida o access token já emitido | Baixa | TTL curto no access token (15 min) + revogação no refresh token |
| 9 | Gateway como ponto único de falha e de autenticação | Baixa | Aceito no escopo; vira `replicas: 2` na fase de Kubernetes |
| 10 | Ordem de subida dos containers: app tenta conectar antes de Postgres/RabbitMQ ficarem prontos | Média | `healthcheck` + `depends_on: condition: service_healthy` |
| 11 | Listagem pública sem índice degrada com volume | Baixa | Índice `(status, event_date)` + paginação obrigatória |
| 12 | Corrida entre pagar e expirar: usuário clica "pagar" no instante do job | Média | Transição condicional com `expires_at > now()` no `WHERE`; perde quem chegar depois, de forma determinística |
| 13 | Devolução dupla de estoque (job e cancelamento manual simultâneos) | Média | A mudança de status é o guarda: só a transação que alterou a linha decrementa |
| 14 | HS256: quem valida o token também consegue emitir | Baixa | Aceito conscientemente pelo escopo; documentado como ponto que viraria RS256/JWKS em produção |
| 15 | Job agendado com múltiplas réplicas executaria a varredura N vezes | Baixa | Sem dano à correção (a transição condicional protege); candidato a ShedLock |

---

## 6. Decisões registradas

| Decisão | Escolha | Justificativa |
|---|---|---|
| Versão do Spring Boot | 3.5.16, não 4.1.0 | Alinhamento com o ecossistema e com a maior parte do material disponível; Spring Cloud 2025.0.3 é o trem compatível |
| Variante do Gateway | Reativa (`gateway-server-webflux`) | Só ela oferece o filtro nativo `RequestRateLimiter` com backend Redis |
| Origem do estoque na reserva | Contador local no `booking-service` | Torna a decisão atômica com a gravação, dispensando transação distribuída |
| Garantia contra overselling | `UPDATE` condicional no PostgreSQL | O lock distribuído é otimização; a correção precisa de garantia durável |
| Etapa de pagamento | Simulado, com expiração da reserva | `PENDING` segura o estoque por um TTL; exercita o ciclo completo sem gateway de pagamento real |
| Cancelamento pelo usuário | Permitido, com devolução ao estoque | Exercita o caminho inverso da concorrência e enriquece o dashboard |
| Assinatura do JWT | HS256 com segredo compartilhado | Simplicidade; o gateway valida sem chamar o `auth-service`. Limitação conhecida no risco #14 |
| Status da reserva | Inclui `EXPIRED`, além dos três originais | Distingue desistência do usuário de timeout do sistema — informação que o dashboard usa |
| Banco do `notification-service` | Nenhum | Não há estado a persistir; um banco vazio seria complexidade sem função |
