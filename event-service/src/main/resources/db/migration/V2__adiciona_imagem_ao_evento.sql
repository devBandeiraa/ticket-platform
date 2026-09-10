-- ============================================================================
--  Capa do evento.
--
--  Nulavel de proposito: um evento sem capa e um estado legitimo, nao um erro.
--  O admin cadastra o evento antes de ter a arte pronta, e o catalogo precisa
--  saber exibi-lo assim mesmo — o frontend desenha um fundo derivado do nome
--  quando o campo vem vazio.
--
--  Guarda a URL, e nao o binario. Servir imagem e problema de CDN, nao de banco
--  de dados: bytes em coluna atravessam a aplicacao inteira a cada consulta,
--  inflam o backup e nao ganham cache de borda.
--
--  500 caracteres acomoda URL assinada de CDN, que costuma carregar parametros
--  de tamanho e expiracao bem alem do caminho em si.
-- ============================================================================

ALTER TABLE events
    ADD COLUMN image_url VARCHAR(500);
