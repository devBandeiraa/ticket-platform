-- Preco do ingresso no espelho local do evento.
--
-- Toda reserva grava um snapshot do preco, e o preco e dado do event-service. Busca-lo por REST
-- a cada reserva colocaria uma chamada de rede no caminho critico e, pior, faria o
-- event-service virar dependencia obrigatoria para vender: com ele fora do ar, nenhuma reserva
-- aconteceria. Guardando o preco junto do estoque, a chamada REST acontece uma unica vez por
-- evento, na hidratacao, exatamente como o mapeamento previu para total_tickets.
--
-- A consequencia e a mesma ja aceita para a capacidade: consistencia eventual. Se o admin
-- reajustar o preco, as reservas seguintes usam o valor hidratado ate a proxima sincronizacao.
ALTER TABLE event_inventory
    ADD COLUMN price NUMERIC(10, 2) NOT NULL DEFAULT 0;

-- O DEFAULT existe so para popular as linhas que ja existissem no momento da migration. Mantê-lo
-- permitiria hidratar um evento sem preco e vender ingresso de graca por omissao.
ALTER TABLE event_inventory
    ALTER COLUMN price DROP DEFAULT;

ALTER TABLE event_inventory
    ADD CONSTRAINT ck_event_inventory_price CHECK (price >= 0);
