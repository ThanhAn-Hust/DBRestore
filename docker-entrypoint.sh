#!/bin/sh
set -e

mkdir -p /root/.db-backup/state /backups

exec java -Dfile.encoding=UTF-8 -jar /app/db-backup.jar "$@"
