#!/bin/sh
set -e

echo "==> [FIAP-X DB Init] Inicializando tabelas e esquemas dos microsserviços..."

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "fiapx_auth" -f /docker-entrypoint-initdb.d/auth-service/01-create-tables.sql
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "fiapx_video" -f /docker-entrypoint-initdb.d/video-api-service/01-create-tables.sql
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "fiapx_video_worker" -f /docker-entrypoint-initdb.d/video-worker-service/01-create-tables.sql
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "fiapx_notification" -f /docker-entrypoint-initdb.d/notification-service/01-create-tables.sql

echo "==> [FIAP-X DB Init] Todas as tabelas e esquemas foram criados com sucesso!"
