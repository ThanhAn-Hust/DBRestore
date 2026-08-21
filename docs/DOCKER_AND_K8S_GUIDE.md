# Docker & Kubernetes Deployment Guide

This guide describes how to deploy `db-backup` as a standalone Docker container, a Docker Compose service, or a Kubernetes Sidecar/CronJob.

---

## 1. Running with Standalone Docker

### One-Shot Backup Execution
```bash
docker run --rm -it \
  -v ~/.db-backup:/root/.db-backup \
  -e AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}" \
  -e AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}" \
  -e AWS_REGION="ap-southeast-1" \
  ghcr.io/dbbackup/db-backup:latest backup \
    --profile prod-mysql \
    --type FULL \
    --encrypt true \
    --passphrase "MySecretPassphrase" \
    --output "s3://prod-backups/mysql/full.sql.gz"
```

### Background Daemon Execution
```bash
docker run -d \
  --name db-backup-daemon \
  --restart unless-stopped \
  -v ~/.db-backup:/root/.db-backup:ro \
  -v /var/backups:/backups \
  -e BACKUP_PASSPHRASE="MySecretPassphrase" \
  -e AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}" \
  -e AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}" \
  ghcr.io/dbbackup/db-backup:latest daemon start
```

---

## 2. Docker Compose Sidecar Deployment

In Docker Compose, run `db-backup` alongside your database container on the same private bridge network:

```yaml
services:
  database:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASS}
      MYSQL_DATABASE: app_db
    volumes:
      - db-data:/var/lib/mysql

  backup-sidecar:
    image: ghcr.io/dbbackup/db-backup:latest
    restart: unless-stopped
    depends_on:
      - database
    environment:
      DB_HOST: database
      DB_USER: root
      DB_PASS: ${DB_PASS}
      BACKUP_PASSPHRASE: ${BACKUP_PASSPHRASE}
      AWS_ACCESS_KEY_ID: ${AWS_ACCESS_KEY_ID}
      AWS_SECRET_ACCESS_KEY: ${AWS_SECRET_ACCESS_KEY}
    volumes:
      - ./config/schedule.yml:/root/.db-backup/schedule.yml:ro
    command: ["daemon", "start"]

volumes:
  db-data:
```

---

## 3. Kubernetes Deployments

### A. Sidecar Pattern (Continuous Daemon with Local IPC)
- See manifest: [`examples/k8s/mysql-backup-sidecar.yaml`](../examples/k8s/mysql-backup-sidecar.yaml)
- Connects directly to `localhost:3306` inside the shared pod network namespace.
- No public database port exposure required.

### B. Kubernetes CronJob (Scheduled Serverless Execution)
- See manifest: [`examples/k8s/postgres-backup-cronjob.yaml`](../examples/k8s/postgres-backup-cronjob.yaml)
- Ideal for managed cloud databases (AWS RDS, GCP Cloud SQL, Azure Database for PostgreSQL).
- Ephemeral container spins up at cron time, streams backup directly to Cloud Storage, and terminates.
