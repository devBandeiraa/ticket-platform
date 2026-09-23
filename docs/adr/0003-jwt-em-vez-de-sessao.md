# ADR 0003 — JWT com refresh rotativo, em vez de sessão no servidor

**Estado:** aceita · **Decidida na** Fase 4, revisitada na Fase 23

## O problema

Cinco serviços precisam saber quem está chamando, e o `event-service` precisa autorizar um
administrador sem nada além do que chegou na requisição.

## As opções

**Sessão no servidor.** Um identificador opaco no cookie, o estado no Redis. Revogar é apagar
uma linha — imediato e total. Em troca, **toda requisição autenticada vira uma ida ao Redis**, e
o Redis passa a ser dependência do caminho de autenticação de todos os serviços. Ele já é
dependência do rate limiting; aqui ele viraria também um ponto único de falha para entrar.

**Token opaco validado no auth-service.** Cada serviço pergunta ao `auth-service` se o token
vale. Revogação imediata e um acoplamento pior: o `auth-service` entra no caminho crítico de
cada chamada, e cair derruba tudo.

**JWT assinado.** O token carrega a identidade; quem tem a chave valida sozinho. Nenhuma ida à
rede por requisição. O custo é conhecido e não tem contorno elegante: **não há como invalidar um
JWT já emitido** sem manter uma lista negra, que reintroduz o estado que se quis eliminar.

## A decisão

JWT assinado em **HS256**, com dois tokens:

| | validade | onde vive |
|---|---|---|
| access token | **15 minutos** | só no cliente, no cabeçalho `Authorization` |
| refresh token | **7 dias** | banco do auth-service, com rotação |

O access token é curto **justamente porque não dá para revogá-lo**. Quinze minutos é o tamanho
da janela em que um token vazado ainda serve — e é o preço que se paga pela validação local.

O gateway valida na borda com a mesma chave. É isso que permite recusar token inválido antes de
qualquer serviço ser acionado.

### A rotação do refresh

Todo refresh **revoga o token apresentado** e emite outro, mesmo em uso legítimo. Isso existe
para detectar roubo: se chegar um refresh token já revogado, ou o dono legítimo já o usou e
alguém tem uma cópia, ou o contrário. Nos dois casos há uma cópia em circulação — e a resposta é
**revogar todas as sessões daquele usuário**.

A revogação em massa roda em transação própria, porque a exceção lançada logo depois desfaria a
revogação se compartilhasse a transação. Seria um `401` sem derrubar sessão nenhuma: o pior dos
dois mundos, alarme sem ação.

### O que a Fase 23 acrescentou

O ingresso digital precisa imprimir quem comprou. Buscar o nome no banco a cada requisição
autenticada desfaria a propriedade que motivou o JWT, então o nome virou o claim `name` — o
nome padronizado pelo OpenID Connect.

Paga o mesmo preço que o claim `role` já pagava: **quem muda o próprio nome só o vê atualizado no
próximo token**. Sem risco novo de vazamento — o token é portado pelo dono, e o nome já é dele.

Todo token em circulação no momento do deploy não tinha o claim. Exigi-lo deslogaria a base
inteira por um campo que só escreve um nome na tela, então o leitor aceita a ausência e devolve
nulo. Há um teste que constrói o token sem o claim justamente para travar isso.

## A limitação que não foi resolvida

**HS256 usa segredo compartilhado, então quem valida também consegue emitir.** O gateway valida
tokens e, com a mesma chave, poderia forjar um token de administrador.

Isso é aceito pelo escopo deste projeto e está registrado como risco #14. Em produção a resposta
seria **RS256 com JWKS**: o `auth-service` assina com a chave privada, os demais validam com a
pública, e ninguém além dele consegue emitir. A troca é de configuração, não de arquitetura — o
código que lê o token não mudaria.

## Consequências

**A favor.** Nenhuma ida à rede para autorizar. O `event-service` decide sobre um admin sozinho.
O gateway recusa token inválido na borda.

**Contra.** Logout não é imediato: o access token já emitido vale até expirar, no máximo quinze
minutos. O que o logout faz de verdade é **revogar os refresh tokens**, impedindo a renovação.

Uma promoção a ADMIN também só vale no próximo token — mesma família de consequência que o nome.

## Onde olhar

- `shared-security/.../JwtTokenReader.java` — validação, comum aos quatro serviços
- `auth-service/.../security/JwtService.java` — emissão e os claims
- `auth-service/.../service/RefreshTokenService.java` — rotação e revogação em massa
- `shared-security/.../JwtTokenReaderTest.java` — token sem o claim continua válido
- Riscos #14, #84 e #85 em [`00-mapeamento.md`](../00-mapeamento.md)
