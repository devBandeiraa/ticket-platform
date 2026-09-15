-- ============================================================================
--  Categoria no evento, e o setor como oferta comercial.
--
--  Duas mudancas que parecem soltas e nao sao: as duas existem para a tela de
--  compra poder dizer o que hoje ela nao tem como dizer.
--
--  A categoria e o que falta para o catalogo ser NAVEGAVEL. Hoje a unica
--  maneira de achar um evento e digitar parte do nome, o que so serve para
--  quem ja sabe o que procura. Quem chega sem saber precisa de uma porta de
--  entrada, e categoria e a mais barata que existe.
--
--  O setor, por sua vez, ja era um nivel de preco — Plateia a 90, Camarote a
--  250 — mas so sabia dizer o preco. Quem escolhe entre os dois quer saber o
--  que muda alem do valor. Descricao, beneficios e faixa dao ao setor o que
--  ele precisa para ser comparado.
--
--  --------------------------------------------------------------------------
--  Por que `benefits` e text[], e nao tabela filha
--  --------------------------------------------------------------------------
--  O caminho convencional em JPA seria `@ElementCollection` com uma tabela
--  `sector_benefits`. Aqui ele custa mais do que entrega.
--
--  Um setor tem dois a cinco beneficios, strings curtas sem identidade propria
--  e cuja unica propriedade relevante e a ordem. Uma tabela filha traria join
--  na leitura e, na escrita, o par delete-insert que o Hibernate emite ao
--  reconciliar colecao — dentro do mesmo flush em que o proprio setor esta
--  sendo atualizado no lugar por `Sector.redefinir`. Essa mistura de estrategias
--  no mesmo flush e exatamente a familia de defeito que ja custou um 500 nesta
--  tabela, quando recriar setor pelo nome violava `uk_sectors_nome`.
--
--  Com `text[]` o setor continua sendo UMA linha, e trocar os beneficios e um
--  UPDATE como qualquer outro campo.
--
--  O custo aceito: `text[]` e do PostgreSQL, entao esta coluna nao migra para
--  outro banco sem reescrita. O projeto ja usa `FOR UPDATE SKIP LOCKED` no
--  booking-service, que tambem nao migra, e roda PostgreSQL em teste,
--  desenvolvimento e producao. Portabilidade de banco nao e promessa que este
--  projeto faca.
--
--  --------------------------------------------------------------------------
--  Por que `tier` e coluna, e nao o setor mais caro
--  --------------------------------------------------------------------------
--  Daria para chamar de VIP o setor de maior preco e nao guardar nada. Mas
--  "mais caro" e "VIP" nao sao a mesma coisa: uma casa pode ter Camarote e
--  Frisa no mesmo patamar, ou um setor caro por ser proximo ao palco sem
--  nenhum beneficio associado. Derivar transformaria uma decisao comercial,
--  que e de quem cadastra, em consequencia aritmetica.
-- ============================================================================

-- ----------------------------------------------------------------------------
--  events.category
--
--  Entra com DEFAULT para as linhas existentes e sai do DEFAULT em seguida: o
--  valor de partida serve a migration, nao ao cadastro. Deixar o DEFAULT
--  permanente faria um evento sem categoria informada virar SHOWS em silencio,
--  e um campo obrigatorio que se preenche sozinho nao e obrigatorio.
--
--  SHOWS como valor de partida por ser a categoria mais generica das seis, e
--  nao por ser verdade sobre os eventos existentes. Em desenvolvimento o seed
--  repetivel corrige cada um logo em seguida; num banco com dados reais, cabe
--  a quem administra revisar.
-- ----------------------------------------------------------------------------

ALTER TABLE events
    ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'SHOWS';

ALTER TABLE events
    ALTER COLUMN category DROP DEFAULT;

-- Texto e nao enum do PostgreSQL: acrescentar uma categoria nova passa a ser
-- mudanca so no codigo. Com tipo enum seria ALTER TYPE, que trava a tabela.
ALTER TABLE events
    ADD CONSTRAINT ck_events_categoria
        CHECK (category IN ('SHOWS', 'FESTIVAIS', 'ESPORTES', 'TECNOLOGIA', 'TEATRO', 'FESTAS'));

-- A listagem publica filtra por categoria dentro do que ja esta publicado,
-- entao o indice util e o par, e nao a categoria sozinha.
CREATE INDEX idx_events_status_categoria ON events (status, category);

-- ----------------------------------------------------------------------------
--  sectors.description, sectors.benefits, sectors.tier
--
--  `description` e `benefits` sao opcionais: um setor chamado "Plateia" a 90
--  reais se explica sozinho, e obrigar uma frase produziria texto de
--  preenchimento. Nulo aqui significa "nao ha o que acrescentar", e a tela
--  simplesmente nao desenha a linha.
--
--  `benefits` aceita nulo e tambem array vazio, e as duas coisas significam o
--  mesmo. A tela trata as duas igual; nao ha CHECK separando-as porque a
--  distincao nao teria consequencia.
-- ----------------------------------------------------------------------------

ALTER TABLE sectors
    ADD COLUMN description TEXT,
    ADD COLUMN benefits    TEXT[],
    ADD COLUMN tier        VARCHAR(20) NOT NULL DEFAULT 'STANDARD';

ALTER TABLE sectors
    ALTER COLUMN tier DROP DEFAULT;

ALTER TABLE sectors
    ADD CONSTRAINT ck_sectors_tier CHECK (tier IN ('STANDARD', 'VIP'));

-- Um beneficio e um rotulo curto de interface, do tipo "Entrada exclusiva" ou
-- "Open bar". O teto de seis impede que a lista vire paragrafo picado: texto
-- corrido cabe em `description`, que e onde a tela sabe desenha-lo.
--
-- Aqui so a QUANTIDADE e verificada. O tamanho de cada rotulo fica no
-- `SectorRequest`, com `@Size` no elemento, porque CHECK do PostgreSQL nao
-- aceita subconsulta e percorrer o array exigiria `unnest` dentro de um
-- SELECT. Escrever uma funcao imutavel so para isso colocaria no esquema uma
-- regra de formulario, que muda com a tela e nao com o dado.
ALTER TABLE sectors
    ADD CONSTRAINT ck_sectors_beneficios_contados
        CHECK (benefits IS NULL OR array_length(benefits, 1) IS NULL
               OR array_length(benefits, 1) <= 6);
