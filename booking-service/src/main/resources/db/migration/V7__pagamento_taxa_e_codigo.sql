-- ============================================================================
--  Taxa de servico, forma de pagamento e codigo do ingresso.
--
--  Tres colunas que o checkout precisa e que ate aqui nao existiam. Duas delas
--  mexem em coisas delicadas, e por isso o texto abaixo e longo.
--
--  --------------------------------------------------------------------------
--  `total_price` muda de significado
--  --------------------------------------------------------------------------
--  Ate esta migration, `total_price` era a soma dos precos dos assentos, e
--  nada mais. A partir dela e a soma MAIS a taxa, e a soma passa a morar em
--  `subtotal`.
--
--  E uma troca de contrato, e nao so uma coluna nova: quem lia `total_price`
--  esperando a soma dos lugares passa a ler outra coisa. O backfill preserva o
--  passado exatamente — `subtotal = total_price` e `fee = 0` — de modo que
--  nenhuma reserva ja feita muda de valor. As antigas ficam com taxa zero
--  porque nunca houve taxa; inventar uma retroativamente reescreveria o que o
--  usuario pagou.
--
--  A checagem `total_price = subtotal + fee` vale para as antigas (x = x + 0) e
--  para as novas. E a invariante inteira do dinheiro nesta tabela, e por isso
--  esta no banco: se um dia alguem gravar as tres colunas por caminhos
--  diferentes e elas discordarem, a linha nao entra.
--
--  --------------------------------------------------------------------------
--  Por que `payment_method` aceita nulo
--  --------------------------------------------------------------------------
--  Porque ele so existe depois de pagar. Uma reserva PENDING nao tem forma de
--  pagamento — quem reservou ainda nao escolheu — e as reservas ja confirmadas
--  antes desta migration foram pagas quando o campo nao existia. Marca-las como
--  CARD seria registrar como fato algo que ninguem informou.
--
--  O CHECK exige a coerencia que importa: confirmada tem forma, nao confirmada
--  nao tem. As linhas antigas ficam de fora dele por `paid_at IS NOT NULL AND
--  payment_method IS NULL` ser justamente o estado delas — ver a condicao.
--
--  --------------------------------------------------------------------------
--  `ticket_code` e o numero do ingresso
--  --------------------------------------------------------------------------
--  Aleatorio, e nao sequencial. Um numero sequencial seria unico de graca e
--  entregaria o volume de vendas a quem comprasse dois ingressos e olhasse a
--  diferenca entre eles.
--
--  Doze caracteres de um alfabeto de 31 dao 31^12, cerca de 7,9 x 10^17
--  combinacoes. Pelo paradoxo do aniversario, a chance de colisao em um milhao
--  de ingressos e da ordem de 6 x 10^-7. A constraint de unicidade existe assim
--  mesmo: probabilidade baixa nao e garantia, e o que ela protege — dois
--  ingressos com o mesmo numero na portaria — nao se conserta depois.
--
--  Nulo enquanto a reserva nao foi paga. O codigo nasce com a confirmacao,
--  porque e o ingresso que ele identifica, e reserva pendente ainda nao e
--  ingresso.
-- ============================================================================

ALTER TABLE bookings
    ADD COLUMN subtotal       NUMERIC(10, 2),
    ADD COLUMN fee            NUMERIC(10, 2),
    ADD COLUMN payment_method VARCHAR(20),
    ADD COLUMN ticket_code    VARCHAR(20);

-- Preserva o passado: o que foi cobrado continua sendo o que foi cobrado.
UPDATE bookings
   SET subtotal = total_price,
       fee      = 0.00;

ALTER TABLE bookings
    ALTER COLUMN subtotal SET NOT NULL,
    ALTER COLUMN fee      SET NOT NULL;

ALTER TABLE bookings
    ADD CONSTRAINT ck_bookings_subtotal_positivo CHECK (subtotal >= 0),
    ADD CONSTRAINT ck_bookings_taxa_positiva     CHECK (fee >= 0),
    -- A invariante do dinheiro. Vale para as linhas antigas (x = x + 0) e para
    -- as novas, e recusa a linha se as tres colunas discordarem entre si.
    ADD CONSTRAINT ck_bookings_total_fecha       CHECK (total_price = subtotal + fee);

ALTER TABLE bookings
    ADD CONSTRAINT ck_bookings_forma_de_pagamento
        CHECK (payment_method IS NULL OR payment_method IN ('CARD', 'PIX'));

-- Reserva nao paga nao pode ter forma de pagamento nem codigo: os dois nascem
-- da confirmacao. O caminho inverso — paga e sem forma — fica PERMITIDO de
-- proposito, porque e o estado das reservas confirmadas antes desta migration,
-- e reescreve-las seria inventar o que ninguem informou.
ALTER TABLE bookings
    ADD CONSTRAINT ck_bookings_pagamento_so_com_pagamento
        CHECK (paid_at IS NOT NULL OR (payment_method IS NULL AND ticket_code IS NULL));

-- Dois ingressos com o mesmo numero na portaria nao se consertam depois.
--
-- No PostgreSQL, nulos sao distintos entre si num indice unico, entao as
-- reservas ainda nao pagas convivem sem colidir. Este mesmo indice serve a
-- busca por codigo, que e o caminho da conferencia na entrada — um segundo
-- indice sobre a mesma coluna so custaria escrita.
CREATE UNIQUE INDEX uk_bookings_ticket_code ON bookings (ticket_code);
