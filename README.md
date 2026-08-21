# 🛡️ DBBackup & Selective Restore Tool

[![CI Verification](https://github.com/dbbackup/db-backup/actions/workflows/ci.yml/badge.svg)](https://github.com/dbbackup/db-backup/actions/workflows/ci.yml)
[![Docker Image](https://img.shields.io/badge/Docker-ghcr.io%2Fdbbackup%2Fdb--backup-blue?logo=docker)](https://github.com/dbbackup/db-backup/pkgs/container/db-backup)
[![Java 21 LTS](https://img.shields.io/badge/Java-21%20LTS-orange?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

An enterprise-grade, memory-efficient **Database Backup & Point-in-Time Restore Engine** supporting **MySQL, MariaDB, and PostgreSQL** with multi-cloud storage sinks (AWS S3, Azure Blob, Google Cloud Storage, Local Disk), zero-buffering segmented AES-256-GCM encryption, SQLite WAL audit logging, chain-aware retention pruning, and daemon cron orchestration.

---

## 🚀 Key Highlights

- **Zero-Buffering Streaming Pipeline**: Pipes database dump process stdout directly through GZIP compression $\rightarrow$ Segmented AES-256-GCM Encryption $\rightarrow$ Cloud Storage without staging intermediate files on local disk.
- **Segmented AES-256-GCM Encryption**: Encrypts 64MB independent segments with derived IVs and end-of-stream truncation markers, preventing memory bloat and cipher replay attacks.
- **Automatic Restore Chain Resolution**: Intelligently discovers full base and incremental parent-child dependencies `[FULL -> INC_1 -> INC_2 -> TARGET]` and executes step-by-step point-in-time recovery.
- **Multi-Cloud Storage Sinks**: Native stream integration with AWS S3 (v2 SDK), Azure Blob Storage, Google Cloud Storage (GCS), and Local/NFS filesystems.
- **Chain-Aware Retention Cohorts**: Protects base FULL backups from deletion as long as any dependent incremental/differential child remains active.
- **Multi-Channel Alerts**: Rich status notifications to Telegram (HTML), Slack (Block Kit), and Discord (Embeds).
- **Process Security**: Never exposes database passwords in process argument lists (POSIX `0600` / Windows User SID ACL isolation with `MYSQL_PWD` and `PGPASSWORD`).

---

## 📦 Quick Start

### 1. Run with Docker (Recommended)

```bash
# Pull the latest image
docker pull ghcr.io/dbbackup/db-backup:latest

# Display available commands
docker run --rm ghcr.io/dbbackup/db-backup:latest help

# Run a one-shot MySQL Backup to AWS S3 with Encryption
docker run --rm -it \
  -e AWS_ACCESS_KEY_ID="your-aws-key" \
  -e AWS_SECRET_ACCESS_KEY="your-aws-secret" \
  ghcr.io/dbbackup/db-backup:latest backup \
    --db-type mysql \
    --host "db.internal" \
    --port 3306 \
    --user root \
    --password "MyRootPass" \
    --database "shop_prod" \
    --type FULL \
    --encrypt true \
    --passphrase "SecretAesPassphrase2026" \
    --output "s3://my-backup-bucket/mysql/shop_prod.sql.gz" \
    --notify "telegram,slack"
```

### 2. Run with Java Fat JAR

```bash
# Verify installation
java -jar db-backup.jar help

# Test Database Connection
java -jar db-backup.jar test-connection --db-type postgresql --host localhost --port 5432 --user postgres --password secret --database testdb

# Restore Backup Chain
java -jar db-backup.jar restore --backup-id "b-1723456789-abc1234" --passphrase "SecretAesPassphrase2026"
```

---

## 📚 Documentation Index

- 📖 [**Complete User & Configuration Guide**](docs/USER_GUIDE.md): CLI commands reference, YAML schemas, storage endpoints, and webhook notification configuration.
- 🐳 [**Docker & Kubernetes Deployment Guide**](docs/DOCKER_AND_K8S_GUIDE.md): Sidecar container pattern, Kubernetes CronJobs, volume persistence, and environment secrets.
- 🚨 [**Disaster Recovery Runbook**](docs/DISASTER_RECOVERY.md): Step-by-step point-in-time recovery, emergency restore workflows, and audit trail verification.
- 📐 [**Architecture Design Specification**](docs/design-spec.md): Complete technical specification, cryptographic framing format, and concurrency models.

---

## 🛠️ Building from Source

```bash
git clone https://github.com/dbbackup/db-backup.git
cd db-backup

# Run tests
mvn clean test

# Build Fat JAR
mvn clean package -DskipTests

# Build Docker Image
docker build -t db-backup:local .
```
