# Kubernetes

A mesma plataforma do `docker compose`, agora em manifestos. Kustomize, sem Helm: o YAML fica
legível para quem abrir o repositório, em vez de escondido atrás de templates Go.

## Subindo

Precisa do Docker rodando, das imagens já construídas e do [kind](https://kind.sigs.k8s.io).

```bash
# 1. imagens (o cluster não tem registry — elas são carregadas de dentro)
docker compose build

# 2. cluster
kind create cluster --config k8s/kind-cluster.yaml

# 3. carregar as imagens no nó
kind load docker-image --name ticket \
  ticket-platform-auth-service:latest \
  ticket-platform-event-service:latest \
  ticket-platform-booking-service:latest \
  ticket-platform-notification-service:latest \
  ticket-platform-api-gateway:latest \
  ticket-platform-frontend:latest

# 4. aplicar
kubectl apply -k k8s/overlays/local
kubectl get pods -n ticket-platform -w
```

Depois disso, os mesmos endereços do compose: **http://localhost:5173** e a API em `:8080`.
São as portas declaradas em `extraPortMappings`, o que faz o CORS do gateway e o `VITE_API_URL`
embutido no frontend continuarem valendo sem reconstruir nada.

Para desfazer: `kind delete cluster --name ticket`.

## Estrutura

```
k8s/
├── kind-cluster.yaml       cluster local, com as portas do host mapeadas
├── base/                   manifestos limpos, válidos em qualquer cluster
│   ├── init-databases.sh   compartilhado com o docker compose
│   └── kustomization.yaml  ConfigMap e Secrets gerados
└── overlays/local/         só o que é específico do kind (NodePort)
```

A base mantém os `Service` como `ClusterIP`, que é o certo onde há um Ingress na frente.
Expor por `NodePort` é concessão ao ambiente local, e por isso vive no overlay.

## O que muda em relação ao compose

| | docker compose | Kubernetes |
|---|---|---|
| Ordem de subida | `depends_on: service_healthy` | `initContainer` esperando a porta |
| Saúde | um `healthcheck` | `startupProbe` + `readiness` + `liveness` |
| Estado | volumes nomeados | `StatefulSet` + `volumeClaimTemplates` |
| Segredos | `.env` | `Secret`, um por dono |
| Réplicas | uma de cada | gateway e booking com duas |

**Três probes, e não uma.** O `startupProbe` cobre a subida da JVM e as migrations do Flyway —
sem ele a liveness começaria a contar durante a migration e mataria o pod no meio dela. Depois,
a readiness decide se o pod recebe tráfego e a liveness decide se ele precisa ser reiniciado:
perguntas diferentes, que um `healthcheck` único não separa.

**Secrets por dono, não um só.** Um `Secret` único seria mais curto e faria todo pod que o
montasse enxergar a senha dos três bancos. Com um por serviço, o pod do `auth-service` não tem
como abrir o `eventdb` — o *database per service* deixa de ser convenção e vira fato.

**Nomes com hash.** Os geradores do Kustomize acrescentam um sufixo ao nome do ConfigMap e dos
Secrets. Mudar um valor gera um nome novo, o Deployment passa a apontar para outro objeto e o
rollout acontece sozinho. Com nome fixo, a alteração ficaria lá sem ninguém reiniciar para lê-la.

## Réplicas

`api-gateway: 2` responde ao risco #9 do mapeamento, que registrava o gateway como ponto único
de falha. Ele é stateless — a sessão vive no JWT e o balde de rate limit no Redis —, então
replicar não exige afinidade de sessão.

`booking-service: 2` é o caso interessante, e não está aqui por vazão. As duas réplicas varrem a
mesma outbox e rodam o mesmo job de expiração:

- **Outbox:** o publicador envia e só então marca. Duas réplicas podem publicar a mesma mensagem
  — e o código diz isso, em comentário, desde a Fase 4. Quem resolve é a deduplicação por
  `message-id` no consumidor, não um lock no produtor.
- **Expiração:** a transição é um `UPDATE` condicional, então só uma réplica consegue expirar
  cada reserva. A outra afeta zero linhas e não devolve estoque duas vezes.

Nada disso é mérito dos manifestos: é o código que já estava preparado. O Kubernetes só ofereceu
o lugar para provar.

## Limitações deste ambiente

- **Um nó.** A plataforma inteira cabe nele, e nós extras custariam memória sem provar nada sobre
  o desenho dos manifestos.
- **Secrets versionados.** Os valores são de desenvolvimento e estão no git de propósito, para
  subir com um comando. Em ambiente real saem daqui — Sealed Secrets, External Secrets ou o
  gerenciador da nuvem.
- **Sem Ingress.** `NodePort` basta para dois serviços expostos. Com mais rotas, um Ingress
  Controller passaria a valer a pena.
- **Sem HPA nem PodDisruptionBudget.** Réplicas fixas. Autoescala exigiria metrics-server e uma
  carga real para calibrar — sem isso seria configuração decorativa.
- **Parou na Fase 9.** Estes manifestos cobrem os cinco serviços originais e a infraestrutura. O
  que veio depois — `payment-simulator`, Jaeger, Prometheus e Grafana — só existe no
  `docker compose`. Na prática, num cluster subido por este guia:
  - **pagar uma reserva falha**, porque `PAYMENT_SERVICE_URL` aponta para um serviço que não
    está lá. Reservar, cancelar e expirar continuam funcionando — a tese do projeto não passa
    pelo pagamento;
  - **não há traces nem métricas**, e a página `/status` responde `503 METRICS_UNAVAILABLE` —
    que é exatamente o comportamento projetado para "perdi a fonte", e não um erro novo.

  Levar as duas coisas para cá é o próximo passo natural, e não é recortar YAML: o Prometheus
  precisaria descobrir os alvos pela API do Kubernetes em vez da lista fixa de
  `monitoring/prometheus/prometheus.yml`, porque num cluster o endereço de um pod muda a cada
  reinício.
