#!/usr/bin/env bash
set -euo pipefail
for service in product order payment inventory review; do
  password_var="${service^^}_DB_PASSWORD"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v role="${service}_owner" -v password="${!password_var}" -v database="${service}_db" <<'SQL'
CREATE ROLE :"role" LOGIN PASSWORD :'password';
CREATE DATABASE :"database" OWNER :"role";
SQL
done
