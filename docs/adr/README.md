# Decisões de arquitetura

Três decisões que moldaram o resto do projeto, cada uma com o problema que a motivou, as opções
descartadas e o custo que ficou.

| | decisão | o custo aceito |
|---|---|---|
| [0001](0001-garantia-contra-overselling.md) | A garantia contra overselling mora no banco, não no lock | A contenção vai para o PostgreSQL |
| [0002](0002-sincrono-e-assincrono-entre-servicos.md) | Síncrono onde a resposta depende, assíncrono onde não depende | A outbox entrega pelo menos uma vez, não exatamente uma |
| [0003](0003-jwt-em-vez-de-sessao.md) | JWT com refresh rotativo, em vez de sessão no servidor | Logout não é imediato; HS256 deixa quem valida também emitir |

## Por que só três, e por que aqui

O [mapeamento](../00-mapeamento.md) registra **106 riscos e 155 decisões**, acumulados fase a
fase. É o diário do projeto: cada linha diz o que foi escolhido e por quê, na ordem em que
aconteceu.

Estes três documentos são outra coisa. São as decisões que, se fossem outras, mudariam tudo o
mais — e por isso merecem o espaço para mostrar **as alternativas que foram descartadas**, que é
justamente o que uma tabela não cabe.

Um ADR aqui não substitui a linha correspondente no mapeamento. Ele a desenvolve.
