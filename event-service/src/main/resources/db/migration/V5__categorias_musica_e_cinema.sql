-- ============================================================================
--  Duas categorias novas: MUSICA e CINEMA.
--
--  O catalogo nasceu com seis categorias, e a tela de descoberta mostrou onde
--  elas faltavam. SHOWS cobria tanto o artista que lota uma casa quanto a roda
--  de samba no quintal, e as duas nao se procuram do mesmo jeito: quem busca um
--  artista digita o nome dele; quem busca "o que fazer no sabado" navega pela
--  cena. Com um rotulo so, a segunda pessoa nao era atendida.
--
--  CINEMA simplesmente nao existia. Uma sessao ao ar livre caia em TECNOLOGIA
--  ou em SHOWS, e as duas respostas estao erradas.
--
--  ----------------------------------------------------------------------------
--   Por que a migration precisa existir
--  ----------------------------------------------------------------------------
--  A coluna e `varchar` com CHECK, e nao tipo enum do PostgreSQL — decisao
--  registrada na V4, cujo motivo era exatamente este: acrescentar categoria
--  deveria ser mudanca de codigo, nao `ALTER TYPE`, que trava a tabela.
--
--  O CHECK, no entanto, enumera os valores aceitos. Sem refaze-lo, um evento
--  gravado como 'MUSICA' viola a restricao e o INSERT falha — o enum do Java
--  aceitaria, e o banco recusaria. Os dois lados precisam listar o mesmo
--  conjunto, e e por isso que o comentario do enum manda vir ate aqui.
--
--  ----------------------------------------------------------------------------
--   DROP e ADD, e nao ALTER
--  ----------------------------------------------------------------------------
--  PostgreSQL nao tem `ALTER CONSTRAINT ... CHECK`. Trocar o predicado exige
--  remover e recriar, e e isso que acontece abaixo. O intervalo entre as duas
--  instrucoes esta dentro da mesma transacao do Flyway, entao nao ha janela em
--  que a tabela fique sem a restricao para quem esta de fora.
--
--  `IF EXISTS` no DROP: um volume criado antes da V4 pode nao ter a restricao
--  com este nome, e falhar ali deixaria a migration pela metade.
--
--  Nenhuma linha existente precisa ser reescrita. O conjunto novo e um
--  SUPERCONJUNTO do antigo: tudo que passava continua passando.
-- ============================================================================

ALTER TABLE events
    DROP CONSTRAINT IF EXISTS ck_events_categoria;

ALTER TABLE events
    ADD CONSTRAINT ck_events_categoria
        CHECK (category IN (
            'MUSICA',
            'SHOWS',
            'FESTIVAIS',
            'ESPORTES',
            'TECNOLOGIA',
            'TEATRO',
            'CINEMA',
            'FESTAS'
        ));
