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
| `price` | numeric(10,2) | copiado do `event-service`; evita uma chamada REST por reserva |
| `synced_at` | timestamptz | |

> **Ajustado na Fase 4.** A coluna `version` prevista aqui foi removida: o `UPDATE` condicional já
> testa a invariante no próprio `WHERE`, e um lock otimista sobre ele guardaria a mesma coisa duas
> vezes — além de que uma query `@Modifying` não incrementa a versão, deixando entidades em memória
> com valor defasado. Em compensação entrou `price`, para que a reserva não dependa do
> `event-service` estar no ar.

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
| `idempotency_key` | varchar, **unique junto com `user_id`** | impede reserva duplicada em retry |
| `created_at` / `updated_at` | timestamptz | |

A unicidade é por usuário, e não global: com chave global, bastaria reaproveitar a chave de outro
para receber a reserva alheia no lugar da sua — idempotência viraria vetor de negação de serviço.

**`outbox_messages`** *(acrescentada na Fase 4)* — eventos a publicar

| Campo | Tipo | Observação |
|---|---|---|
| `id` | UUID PK | |
| `aggregate_type` / `aggregate_id` | varchar / UUID | o que gerou o evento; permite achar tudo de uma reserva sem varrer JSON |
| `type` | varchar | vira a routing key, ex. `booking.confirmed` |
| `payload` | jsonb | serializado na gravação, congelando o que aconteceu naquele instante |
| `status` | enum `PENDING` \| `PUBLISHED` \| `FAILED` | |
| `attempts` / `last_error` | int / text | distinguem broker com problema de mensagem que não passa |
| `created_at` / `published_at` | timestamptz | |

Gravada na **mesma transação** que confirma a reserva. É o que impede os dois desfechos ruins:
notificar um pagamento que não aconteceu, ou confirmar um pagamento sem notificar ninguém.

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
| ~~GET~~ | ~~`/internal/events/{id}`~~ | 🔗 | **Não implementado.** O `booking-service` reusa `GET /events/{id}`: o endpoint público já devolve capacidade e preço e já filtra por publicados, que é exatamente a regra desejada — não se vende ingresso de rascunho. Um endpoint a mais entregaria os mesmos dados sob outra URL |

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
| GET | `/admin/bookings?eventId=&status=&page=&size=` | 🔒 ADMIN | alimenta o dashboard |

> **Implementado na Fase 4.** A listagem administrativa ficou sob `/admin`, e não em `GET /bookings`
> como previsto acima, acompanhando a separação já adotada no `event-service` entre `/events` e
> `/admin/events`. A regra por prefixo protege por construção: um endpoint administrativo novo
> nasce protegido só por morar ali, enquanto uma exceção para um verbo específico em `/bookings`
> dependeria de alguém lembrar dela ao adicionar o próximo.

Códigos de negócio devolvidos: `409 SOLD_OUT`, `409 LOCK_TIMEOUT`, `409 BOOKING_EXPIRED`,
`409 BOOKING_CANCELLED`, `409 BOOKING_ALREADY_CONFIRMED`, `404 EVENT_NOT_AVAILABLE`,
`404 BOOKING_NOT_FOUND`, `400 INVALID_IDEMPOTENCY_KEY`, `503 EVENT_SERVICE_UNAVAILABLE`.

`POST /bookings` devolve `201` na criação e `200` quando a mesma `Idempotency-Key` é
reapresentada — um cliente que conte reservas pela contagem de `201` continua contando certo.

### `notification-service`

Sem API HTTP de negócio — apenas `/actuator/health`. Comunica-se exclusivamente por RabbitMQ.

**Topologia de filas** *(implementada na Fase 5)*

| Recurso | Nome | Papel |
|---|---|---|
| Exchange | `booking.exchange` (topic, durable) | Declarado pelos dois lados; declarar exchange é idempotente no RabbitMQ |
| Fila | `notification.booking.confirmed` | Ligada por `booking.confirmed`, com dead-letter configurado |
| DLX | `notification.dlx` (direct, durable) | Uma mensagem que falhou tem destino único; não há roteamento a decidir |
| DLQ | `notification.booking.confirmed.dlq` | Existe para ser olhada por uma pessoa |

Quem consome declara a própria fila e o próprio binding; o `booking-service` declara apenas o
exchange. É o que permite um consumidor novo entrar sem exigir mudança no produtor.

**Retentativa:** 3 tentativas com backoff exponencial (1s → 2s → 4s, teto de 10s), e então DLQ.
`default-requeue-rejected: false` — com requeue ligado, uma mensagem defeituosa voltaria para a
fila e seria reentregue em laço. Payload malformado vai direto para a DLQ, sem retentar: retentar
não conserta JSON quebrado.

**Idempotência do consumidor.** A outbox entrega *ao menos uma vez*, então a mesma mensagem pode
chegar duas vezes. A deduplicação usa `SET NX PX` no Redis sobre o `message-id`, com TTL de 24h.
Não é em memória porque as duas situações em que duplicatas aparecem — reinício do processo e mais
de uma réplica na mesma fila — são exatamente aquelas em que um cache local não serve.

A marca é gravada **antes** do envio e **devolvida** se ele falhar. Sem essa devolução, a
retentativa seria reconhecida como duplicata e descartada, perdendo a notificação justamente
quando o retry existe para salvá-la.

### `api-gateway`

Não publica API própria. Encaminha para os demais, sob o prefixo `/api`, retirado por
`StripPrefix=1` — cada serviço continua publicando as rotas que já publicava, e nenhum precisa
saber que existe um gateway na frente.

**Tabela de rotas** *(implementada na Fase 6)*

A ordem importa: vence a primeira rota cujo predicado casar.

| Ordem | Predicado | Destino |
|---|---|---|
| 1 | `/api/auth/login`, `/api/auth/register` | `auth-service` — com balde de limite próprio |
| 2 | `/api/auth/**` | `auth-service` |
| 3 | `/api/events/*/availability` | `booking-service` |
| 4 | `/api/bookings/**` | `booking-service` |
| 5 | `/api/admin/bookings/**` | `booking-service` |
| 6 | `/api/admin/events/**` | `event-service` |
| 7 | `/api/events/**` | `event-service` |

A rota 3 vem antes da 7 de propósito: `/events/{id}/availability` é o único caminho sob `/events`
que pertence ao `booking-service`, que é quem tem o contador de estoque. Invertida a ordem, o
`event-service` receberia a chamada e responderia `404`.

**Autentica, mas não autoriza.** O gateway confere *quem* está chamando; *o que* cada um pode
fazer continua decidido em cada serviço. Replicar aqui as regras de rota criaria uma segunda cópia
delas, e duas cópias divergem — bastaria adicionar um endpoint administrativo e esquecer de
espelhar a regra para o gateway liberar o que o serviço julgava protegido.

Por isso requisição **sem token passa**: quem sabe se a rota exige identificação é o destino. Já
token **inválido para no gateway** com `401 INVALID_TOKEN` — expirado, adulterado ou de outro
emissor não vira válido em serviço nenhum, e encaminhá-lo só gastaria uma ida à rede.

Os serviços seguem validando o JWT por conta própria. Não é redundância desperdiçada: é o que
impede que alcançar um serviço diretamente, por dentro da rede, contorne a autenticação.

**Cabeçalhos de identidade.** `X-User-Id`, `X-User-Email` e `X-User-Role` são *sempre* removidos
da requisição que chega e reescritos pelo gateway a partir do token. Aceitar o valor enviado pelo
cliente permitiria a qualquer um mandar `X-User-Role: ADMIN` e ser tratado como administrador.

**Rate limiting** — token bucket no Redis, em dois baldes independentes:

| Balde | Chave | Política | Por quê |
|---|---|---|---|
| Geral | `userId` se autenticado, senão IP | 20 req/s, rajada de 40 | Contar sempre por IP puniria colegas atrás de um mesmo NAT. Depois do login existe identidade melhor que o endereço de rede |
| Login e cadastro | sempre IP | 5 por minuto, rajada de 2 | Quem tenta adivinhar senha ainda não tem token; chavear pelo e-mail informado deixaria trocar de alvo a cada tentativa e nunca encostar no limite |

São dois baldes porque o `RedisRateLimiter` monta a chave do Redis a partir do resolvedor, e não
da rota: dois limites com a mesma chave dividiriam o mesmo balde, e o estreito seria reabastecido
pela taxa generosa do outro.

O IP vem da conexão, e não de `X-Forwarded-For`, que qualquer cliente pode forjar para ganhar um
balde novo a cada requisição. Atrás de um proxy real, o certo é declarar
`spring.cloud.gateway.server.webflux.trusted-proxies` e ler dali.

Com o Redis fora do ar, o `RedisRateLimiter` deixa passar. Mesma postura já adotada no lock e na
deduplicação: entre parar a plataforma e ficar sem limite por um intervalo, o dano do segundo é
menor.

**CORS** é resolvido aqui, e não em cada serviço. O navegador só conversa com o gateway, então é o
único lugar de onde a resposta do preflight pode sair.

### `frontend`

React 19 + TypeScript + Tailwind 4, servido pelo Vite. Consome **apenas** o gateway: nenhuma
chamada direta a um serviço.

**Telas** *(implementadas na Fase 7)*

| Rota | Acesso | Papel |
|---|---|---|
| `/` | 🌐 | Catálogo com busca e paginação |
| `/eventos/:id` | 🌐 | Detalhe, disponibilidade ao vivo e reserva |
| `/login`, `/cadastro` | 🌐 | Sessão |
| `/minhas-reservas` | 🔒 | Contagem regressiva, pagar e cancelar |
| `/admin/eventos` | 🔒 ADMIN | CRUD, publicar e cancelar |
| `/admin/reservas` | 🔒 ADMIN | Dashboard com filtros e estoque do evento |
| `/demo/concorrencia` | 🌐 | Dispara N reservas simultâneas e mostra o resultado |

**A rota protegida não é segurança.** Qualquer pessoa edita o JavaScript da própria aba e alcança
a tela. A garantia está no backend, onde cada serviço valida o token e confere o papel. O objetivo
aqui é não oferecer ao usuário um caminho que termina em `403`.

**Sessão.** O access token vive **apenas em memória**; o refresh token, no `localStorage`. Não é
teatro: um XSS alcança o `localStorage`, então guardar ali o token de acesso entregaria uma
credencial pronta para uso. O que sobra no disco é um refresh token, e esse o `/auth/logout`
revoga de verdade — o access token ninguém revoga, e é por isso que ele não deve ficar guardado.
O custo é recarregar a página perder o access token, resolvido renovando na subida do app.

A renovação em andamento é **compartilhada** entre chamadas simultâneas. Sem isso, uma tela que
dispara três requisições com o token vencido faria três renovações, e como o `auth-service`
*rotaciona* o refresh token, a primeira invalidaria o que as outras duas tinham em mãos —
derrubando a sessão de quem não fez nada de errado.

**Idempotency-Key** é gerada por *intenção de compra*, e fica fixa enquanto a intenção não muda.
Se a resposta se perder e o usuário clicar de novo, a mesma chave devolve a reserva existente.
Trocar a quantidade é outra intenção, e ganha chave nova.

**A contagem regressiva recalcula a diferença a cada tick**, em vez de decrementar um contador.
Com a aba em segundo plano o navegador estrangula os timers, e um contador decrescente atrasaria.
O zero é apenas visual: quem impede o pagamento de uma reserva vencida é o
`WHERE expires_at > now()` no banco.

**A demo de concorrência** dispara N reservas com `Promise.allSettled` e classifica as respostas
por código. É a tese do projeto na tela — o mesmo que o teste de 200 threads do `booking-service`
verifica, mas visível para quem não vai rodar um teste JUnit. Contabiliza `429` em separado: acima
da rajada de 40 o rate limiter recusa antes de a requisição chegar ao `booking-service`, e sem
essa distinção o resultado pareceria dizer algo sobre estoque quando fala do limite da borda.

**Sem proxy do Vite**, de propósito: o frontend chama `http://localhost:8080/api` diretamente, e
assim o desenvolvimento exercita o CORS configurado no gateway. Um proxy esconderia uma origem mal
liberada até a primeira publicação.

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

O gateway responde por conta própria em dois casos — `401 INVALID_TOKEN` e `429
RATE_LIMIT_EXCEEDED` — e usa o mesmo formato. O `429` exigiu um filtro dedicado: o
`RequestRateLimiter` recusa definindo o status e encerrando a resposta **vazia**, e o cliente
receberia um corpo em branco justamente no erro em que precisa explicar algo ao usuário.

O que se compartilha entre servlet e reativo é o contrato — o record `ApiError` —, não o mecanismo
de escrita, que é necessariamente diferente nos dois modelos.

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

> **Estado na Fase 4.** A hidratação preguiçosa está implementada; a mensagem `event.updated`
> **não**. Enquanto não existir, uma alteração de capacidade ou de preço no `event-service` não
> chega ao `booking-service`, que segue usando os valores hidratados. Fica registrado como lacuna
> conhecida, a ser fechada quando a mensageria entre serviços for montada — o `event-service`
> ainda não publica evento algum.

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

O `api-gateway` foge da forma acima, e deve fugir: não tem controller, domínio nem repositório,
porque não tem negócio próprio. A estrutura acompanha o que ele de fato faz.

```
src/main/java/com/devbandeiraa/apigateway/
├── security/        # valida o token e afirma a identidade em cabeçalhos
├── ratelimit/       # resolvedores de chave dos baldes
└── web/             # respostas de erro escritas pelo próprio gateway
```

As rotas ficam em `application.yml`, e não em Java. Uma tabela de rotas é configuração, e em YAML
ela se lê como tabela — predicado, destino e política lado a lado.

O `frontend` segue a mesma ideia de organizar pelo que existe, e não por um molde:

```
frontend/src/
├── api/             # contratos, cliente HTTP e um modulo por dominio
├── auth/            # sessao e guarda de rotas
├── componentes/     # pecas visuais repetidas e formatacao
└── paginas/         # uma por rota; admin/ para as restritas
```

Nomes em português acompanham o resto do projeto — a exceção é `api/tipos.ts`, onde os campos
mantêm o nome que vem no JSON. Traduzi-los exigiria um mapeamento a cada resposta e criaria dois
vocabulários para a mesma coisa.

---

## 5. Riscos técnicos

| # | Risco | Gravidade | Mitigação |
|---|---|---|---|
| 1 | Lock do Redis expira durante a transação (TTL menor que a duração) → duas threads na seção crítica | Alta | `UPDATE` condicional + `CHECK constraint` garantem a correção mesmo assim; o lock é otimização, não a garantia |
| 2 | Liberar o lock de outro processo: se o TTL expirou e outro já o adquiriu, um `DEL` cego mata o lock alheio | Alta | Liberação por script Lua com comparação de token (compare-and-delete) |
| 3 | Redis indisponível → reservas param | Média | **Resolvido na Fase 4:** degrada para "só banco". A reserva segue sem lock, correta pelo `UPDATE` condicional, apenas mais contenciosa. Registrado em `WARN` e na métrica `booking.lock.degradacoes` |
| 4 | Reserva confirmada mas `publish` no RabbitMQ falha → notificação perdida silenciosamente | Alta | **Resolvido na Fase 4:** transactional outbox. O evento é gravado na mesma transação que confirma a reserva; um publicador separado o entrega com retry |
| 5 | Deriva de estoque entre serviços (mensagem perdida, ou admin reduz capacidade abaixo do já vendido) | Média | Validar no `event-service` que o novo `total_tickets` ≥ vendidos; endpoint admin de reconciliação |
| 6 | Retry do cliente cria reserva duplicada | Média | `Idempotency-Key` com unique constraint |
| 7 | Teste de concorrência intermitente — H2 não reproduz o isolamento do PostgreSQL | Média | Testcontainers com Postgres e Redis reais; rodar o teste várias vezes no checkpoint |
| 8 | JWT sem revogação: logout não invalida o access token já emitido | Baixa | TTL curto no access token (15 min) + revogação no refresh token |
| 9 | Gateway como ponto único de falha e de autenticação | Baixa | Aceito no escopo; vira `replicas: 2` na fase de Kubernetes. **Atenuado na Fase 6:** cada serviço segue validando o JWT, então o gateway não é a *única* barreira de autenticação — apenas a primeira |
| 10 | Ordem de subida dos containers: app tenta conectar antes de Postgres/RabbitMQ ficarem prontos | Média | `healthcheck` + `depends_on: condition: service_healthy` |
| 11 | Listagem pública sem índice degrada com volume | Baixa | Índice `(status, event_date)` + paginação obrigatória |
| 12 | Corrida entre pagar e expirar: usuário clica "pagar" no instante do job | Média | Transição condicional com `expires_at > now()` no `WHERE`; perde quem chegar depois, de forma determinística |
| 13 | Devolução dupla de estoque (job e cancelamento manual simultâneos) | Média | A mudança de status é o guarda: só a transação que alterou a linha decrementa |
| 14 | HS256: quem valida o token também consegue emitir | Baixa | Aceito conscientemente pelo escopo; documentado como ponto que viraria RS256/JWKS em produção |
| 15 | Job agendado com múltiplas réplicas executaria a varredura N vezes | Baixa | Sem dano à correção (a transição condicional protege); candidato a ShedLock |
| 16 | Entidade com id atribuído faz o `save()` do Spring Data virar `merge`, e um `UPDATE` concorrente zera `reserved_tickets` | Alta | **Encontrado pelo teste de concorrência na Fase 4** — vendeu 70 ingressos para um evento de 50. `EventInventory` implementa `Persistable`, forçando `persist` e violação de chave primária em vez de sobrescrita |
| 17 | `expires_at` devolvido no `201` diverge do mesmo campo lido depois, por o PostgreSQL truncar nanossegundos | Baixa | Instante truncado para microssegundos na criação da reserva |
| 18 | Entrega ao menos uma vez da outbox → usuário notificado duas vezes do mesmo pagamento | Média | **Resolvido na Fase 5:** deduplicação por `message-id` no Redis, com a marca devolvida em caso de falha para não atrapalhar a retentativa |
| 19 | Mensagem defeituosa reentregue em laço travaria a fila e as demais notificações | Média | **Resolvido na Fase 5:** `default-requeue-rejected: false` + DLQ após 3 tentativas; falha de conversão vai direto para a DLQ |
| 20 | Cliente envia `X-User-Role: ADMIN` por conta própria e é tratado como administrador | Alta | **Resolvido na Fase 6:** o gateway apaga os três cabeçalhos de identidade de toda requisição que chega, antes de escrever os seus. Coberto por teste, inclusive no caso de token válido somado a cabeçalho forjado |
| 21 | Rota genérica declarada antes da específica sequestra o tráfego da segunda | Média | **Resolvido na Fase 6:** `/api/events/*/availability` vem antes de `/api/events/**`, com teste que falha se a ordem for invertida — o erro seria um `404` do serviço errado, difícil de atribuir à tabela de rotas |
| 22 | `X-Forwarded-For` forjado daria um balde de rate limit novo a cada requisição | Média | **Resolvido na Fase 6:** a chave usa o endereço da conexão. Atrás de proxy real, exige declarar `trusted-proxies` — registrado como pendência da Fase 8 |
| 23 | Uma cópia das regras de autorização no gateway divergiria das dos serviços | Média | **Evitado na Fase 6:** o gateway autentica e não autoriza. Requisição sem token é encaminhada, e quem exige identificação é o serviço |
| 24 | Limite de 5/min no login atrapalha desenvolvimento e testes manuais | Baixa | Aceito. Todos os valores vêm de variável de ambiente; nos testes automatizados o custo por requisição é ajustado para não depender de espera |
| 25 | Renovações simultâneas de token derrubam a sessão, porque o refresh é rotacionado | Alta | **Resolvido na Fase 7:** a renovação em andamento é compartilhada entre as chamadas. Coberto por teste que falha se a deduplicação sair |
| 26 | XSS no frontend alcançaria o access token guardado em disco | Média | **Atenuado na Fase 7:** o access token vive só em memória. O refresh token, que fica no `localStorage`, ao menos é revogável pelo `/auth/logout` |
| 27 | Clique repetido em "Reservar" criaria duas reservas | Média | **Resolvido na Fase 7:** a `Idempotency-Key` é fixa por intenção de compra, e não por requisição |
| 28 | A demo de concorrência esbarra no rate limiter e o resultado engana | Média | **Resolvido na Fase 7:** os `429` são contados em separado, com aviso na tela; o limite padrão de rajada é 40 |

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
| Redis fora do ar na reserva *(Fase 4)* | Degradar para só banco | Recusar com `503` faria do cache um ponto único de falha da operação mais importante do sistema, e contradiria a tese de que a garantia mora no banco |
| Publicação de `booking.confirmed` *(Fase 4)* | Transactional outbox | Publisher confirms deixaria a reserva `CONFIRMED` sem que ninguém fosse avisado, em silêncio. A outbox custa uma tabela e um job, e é o tipo de solução que o projeto existe para mostrar |
| Preço na reserva *(Fase 4)* | Copiado para o `event_inventory` na hidratação | Buscá-lo por REST a cada reserva colocaria rede no caminho crítico e faria o `event-service` virar dependência obrigatória para vender |
| `event_inventory.version` *(Fase 4)* | Removido | O `UPDATE` condicional já testa a invariante no `WHERE`; um lock otimista sobre ele guardaria o mesmo duas vezes, e uma query `@Modifying` sequer incrementaria a versão |
| Escopo da `Idempotency-Key` *(Fase 4)* | Única por usuário, não global | Chave global permitiria reaproveitar a de outro e receber a reserva alheia, transformando idempotência em negação de serviço |
| Leitura do evento pelo `booking-service` *(Fase 4)* | Reusa `GET /events/{id}` | O endpoint público já devolve capacidade e preço e já filtra por publicados, que é exatamente a regra desejada. `/internal/events/{id}` não se justificou |
| Tratadores de erro comuns *(Fase 4)* | Extraídos para o `shared-security` | Regra de três: as duas primeiras cópias ficaram duplicadas de propósito; com o terceiro serviço a duplicação passou a ter custo real |
| Contato do usuário na notificação *(Fase 5)* | Não resolvido: registra o `userId` | A notificação é um log estruturado, então o e-mail não é necessário. Buscá-lo no `auth-service` só para enriquecer uma linha de log seria complexidade sem função — o mesmo argumento que justificou o serviço não ter banco |
| Duplicatas no consumidor *(Fase 5)* | `SET NX PX` no Redis, TTL de 24h | Fecha do lado do consumidor a janela que a outbox abre. Redis já está no stack, então não é dependência nova; memória não serviria, porque falha no reinício e com múltiplas réplicas |
| Tipo do evento entre serviços *(Fase 5)* | Record duplicado nos dois lados | O contrato é a mensagem no broker, não uma classe Java. Um tipo compartilhado faria mudar o evento exigir recompilar e reimplantar os dois serviços juntos |
| Redis fora do ar na deduplicação *(Fase 5)* | Notifica mesmo assim | Mesma postura do lock: entre arriscar uma notificação repetida e não notificar, o dano da primeira é menor. Registrado em `WARN` e em métrica |
| Papel do gateway *(Fase 6)* | Autentica, não autoriza | Replicar as regras de rota criaria uma segunda cópia delas, e um endpoint novo cujo espelho fosse esquecido ficaria aberto. A validação na borda ainda tem função própria: recusa cedo o que já se sabe inválido e produz a chave do rate limiter |
| Validação de JWT nos serviços *(Fase 6)* | Mantida, mesmo com o gateway validando | É o que impede que alcançar um serviço por dentro da rede contorne a autenticação. Um gateway cuja única garantia é "ninguém fala com o backend sem passar por mim" transforma qualquer brecha de rede em acesso irrestrito |
| Chave do rate limiting *(Fase 6)* | Híbrida, com balde estrito no login | Cobre os dois abusos reais e distintos: força bruta de senha, que precisa de IP porque o atacante ainda não tem token, e enxurrada de requisições autenticadas, que precisa de `userId` para não punir quem divide o NAT |
| Corpo do `429` *(Fase 6)* | Filtro que decora a resposta | O `RequestRateLimiter` encerra a resposta vazia, e recusa por limite não lança exceção — nenhum tratador de erro seria chamado. Decorar a resposta antes de ele agir é o único ponto em que ainda há onde escrever |
| Bean servlet no `shared-security` *(Fase 6)* | Isolado sob `@ConditionalOnClass` | O `SecurityErrorResponder` implementa interfaces de servlet, e a auto configuração o criaria também no gateway reativo, derrubando-o na subida. A condição precisa estar numa classe aninhada: no método, avaliar o `@Bean` já carregaria o tipo de retorno |
| CORS *(Fase 6)* | Só no gateway | O navegador só conversa com o gateway. Configurá-lo nos serviços seria manter em três lugares uma regra que nenhum navegador chega a consultar |
| Guarda do access token *(Fase 7)* | Só em memória | Um XSS alcança o `localStorage`; o access token guardado ali seria uma credencial pronta e **irrevogável**. O refresh token fica em disco porque ao menos é revogável no servidor |
| Tipos da API no frontend *(Fase 7)* | Cópia manual, não gerada | Mesmo argumento do record duplicado da Fase 5: o contrato é o JSON que atravessa a rede. Gerar do código Java acoplaria o build do frontend ao do backend |
| Camada de dados *(Fase 7)* | TanStack Query | O que a mão escreveria — cache, invalidação após mutação, estado de carregamento — é justamente onde os bugs sutis moram. Invalidar `disponibilidade` depois de reservar é uma linha, e não um `useEffect` a mais |
| Endereço da API no desenvolvimento *(Fase 7)* | Gateway direto, sem proxy do Vite | Um proxy tornaria toda chamada same-origin e esconderia uma origem mal liberada no CORS até a primeira publicação |
| Escopo dos testes do frontend *(Fase 7)* | Cliente HTTP e formatação | É onde há lógica que falha de forma silenciosa e cara. Testar renderização de página verificaria sobretudo o próprio React |
