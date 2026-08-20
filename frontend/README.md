# frontend

Interface da plataforma de ingressos. React 19, TypeScript, Tailwind 4 e Vite.

Consome **apenas** o `api-gateway`, em `/api`. Nenhum serviço é chamado diretamente — trocar a
porta de um deles não muda nada aqui.

## Rodando

Antes, suba a infraestrutura e o backend a partir da raiz do repositório:

```bash
docker compose up -d
./mvnw clean install

# o auth-service precisa do profile `dev` para o seed do administrador
SPRING_PROFILES_ACTIVE=dev java -jar auth-service/target/auth-service-0.0.1-SNAPSHOT.jar
java -jar event-service/target/event-service-0.0.1-SNAPSHOT.jar
java -jar booking-service/target/booking-service-0.0.1-SNAPSHOT.jar
java -jar notification-service/target/notification-service-0.0.1-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar
```

Depois, aqui:

```bash
npm install
npm run dev     # http://localhost:5173
npm test        # vitest
npm run build   # tsc + vite build
```

A porta **5173 não é opcional**: é a origem liberada no CORS do gateway. Para usar outra, ajuste
`CORS_ALLOWED_ORIGINS` lá também, ou o navegador barra todas as chamadas.

O endereço da API vem de `VITE_API_URL` — veja `.env.example`.

## Entrando

O administrador semeado em desenvolvimento é `admin@ticket.dev` / `admin@ticket.dev123`. Contas
criadas pelo cadastro público nascem sempre como `USER`.

## Telas

| Rota | Acesso |
|---|---|
| `/` | pública — catálogo |
| `/eventos/:id` | pública — detalhe e reserva |
| `/login`, `/cadastro` | pública |
| `/minhas-reservas` | exige sessão |
| `/admin/eventos` | exige `ADMIN` |
| `/admin/reservas` | exige `ADMIN` |
| `/demo/concorrencia` | pública, mas reservar exige sessão |

A guarda de rota aqui **não é segurança** — qualquer pessoa edita o JavaScript da própria aba. A
garantia está no backend. O objetivo é não oferecer um caminho que termina em `403`.

## `/demo/concorrencia`

Dispara N reservas simultâneas contra o mesmo evento e mostra quantas confirmaram, quantas
receberam `409 SOLD_OUT` e quanto sobrou no estoque.

É a tese do projeto: não se vende mais que a capacidade, e a garantia não vem do lock distribuído
— ele é otimização — e sim de um `UPDATE` condicional com `CHECK constraint` no PostgreSQL.

Para ver o efeito, use um evento de capacidade pequena e dispare mais reservas que ingressos.
Acima de 40 requisições o rate limiter do gateway começa a recusar antes de a chamada chegar ao
`booking-service`; a tela conta esses `429` em separado e avisa.

## Decisões

Estão em [`docs/00-mapeamento.md`](../docs/00-mapeamento.md), na seção `frontend` — em especial
por que o access token vive só em memória, por que a renovação é compartilhada entre chamadas
simultâneas e por que não há proxy do Vite.
