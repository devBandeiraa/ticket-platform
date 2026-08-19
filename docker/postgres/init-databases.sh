#!/bin/bash
# Cria um banco e um usuario dedicados para cada microsservico.
#
# Executado pelo entrypoint do Postgres apenas na primeira subida, enquanto o
# volume ainda esta vazio. Para reexecutar: `docker compose down -v`.
#
# Cada servico recebe credenciais proprias e so enxerga o proprio banco. O
# PostgreSQL nao permite JOIN entre bancos distintos, entao o isolamento do
# padrao "database per service" e garantido pelo proprio SGBD.

set -euo pipefail

criar_banco() {
  local nome_banco="$1"
  local usuario="$2"
  local senha="$3"

  echo "  -> criando banco '${nome_banco}' com usuario '${usuario}'"

  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
    CREATE USER ${usuario} WITH PASSWORD '${senha}';
    CREATE DATABASE ${nome_banco} OWNER ${usuario};
    GRANT ALL PRIVILEGES ON DATABASE ${nome_banco} TO ${usuario};

    -- Por padrao o Postgres concede CONNECT ao papel PUBLIC, o que deixaria o
    -- usuario de um servico abrir o banco de outro. Revogar torna o isolamento
    -- do "database per service" real, e nao apenas uma convencao.
    REVOKE CONNECT ON DATABASE ${nome_banco} FROM PUBLIC;
EOSQL

  # A partir do Postgres 15 o schema public nao e mais gravavel por padrao para
  # usuarios comuns; sem isto o Flyway falha ao criar as tabelas.
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${nome_banco}" <<-EOSQL
    GRANT ALL ON SCHEMA public TO ${usuario};
    ALTER SCHEMA public OWNER TO ${usuario};
EOSQL
}

echo "== inicializando bancos da plataforma de ingressos =="

criar_banco "${AUTH_DB_NAME}"    "${AUTH_DB_USER}"    "${AUTH_DB_PASSWORD}"
criar_banco "${EVENT_DB_NAME}"   "${EVENT_DB_USER}"   "${EVENT_DB_PASSWORD}"
criar_banco "${BOOKING_DB_NAME}" "${BOOKING_DB_USER}" "${BOOKING_DB_PASSWORD}"

echo "== bancos criados com sucesso =="
