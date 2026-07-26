#!/bin/bash
# 운영 DB와 분리된 dev 스키마를 만든다. 로컬 개발은 SSH 터널로 이 DB에 붙는다.
# NOTE: (cjkang) initdb 스크립트는 데이터 볼륨이 비어 있을 때만 실행된다.
# 이미 기동한 뒤라면 `docker compose exec postgres psql`로 직접 만들어야 한다.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE ROLE carix_dev LOGIN PASSWORD '${CARIX_DEV_PASSWORD}';
    CREATE DATABASE carix_dev OWNER carix_dev;
EOSQL
