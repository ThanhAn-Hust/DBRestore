# DBBackup User & Configuration Guide

This comprehensive guide details the configuration files, CLI options, encryption standards, cloud storage configurations, and notification webhooks supported by `db-backup`.

---

## 1. Configuration Profiles (`~/.db-backup/config.yml`)

Instead of specifying host, user, and credentials on every CLI invocation, you can define profiles in `~/.db-backup/config.yml`. Environment variable substitutions (e.g. `${ENV_VAR}` or `${ENV_VAR:default}`) are automatically expanded.

```yaml
profiles:
  mysql-prod:
    db-type: mysql
    host: db-primary.internal.company.com
    port: 3306
    username: ${DB_USER:dbbackup_agent}
    password: ${DB_PASS:s3cur3p@ssw0rd}
    database: ecommerce_prod

  pg-analytics:
    db-type: postgresql
    host: 10.0.10.50
    port: 5432
    username: postgres
    password: ${PG_PASSWORD:analyticsPass2026}
    database: datalake
```

---

## 2. Daemon Schedule Configuration (`~/.db-backup/schedule.yml`)

The background daemon parses `schedule.yml` and executes jobs according to standard 5-part or 6-part cron expressions.

```yaml
jobs:
  - id: daily-mysql-full
    profile: mysql-prod
    cron: "0 0 2 * * *"               # 02:00 AM daily
    backup-type: FULL
    single-transaction: true
    encrypt: true
    passphrase: ${BACKUP_PASSPHRASE}
    output: "s3://company-prod-backups/mysql/daily.sql.gz"
    on-overlap: SKIP                  # Options: SKIP, QUEUE, CANCEL_PREVIOUS
    retention:
      keep-last: 7                    # Retain last 7 complete chains
      retention-days: 30              # Delete cohorts older than 30 days
    notifications:
      - telegram
      - slack

  - id: hourly-mysql-incremental
    profile: mysql-prod
    cron: "0 0 * * * *"               # Every hour
    backup-type: INCREMENTAL
    encrypt: true
    passphrase: ${BACKUP_PASSPHRASE}
    output: "s3://company-prod-backups/mysql/incremental.sql.gz"
    on-overlap: QUEUE
    notifications:
      - slack
```

---

## 3. Storage Destination URIs

`db-backup` detects the destination protocol automatically via URI scheme:

| Storage Type | URI Format Example | Required Environment Variables |
|---|---|---|
| **Local / NFS** | `file:///var/backups/db.sql.gz` | None |
| **AWS S3** | `s3://my-bucket/backups/db.sql.gz` | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION` |
| **Azure Blob** | `azure://container-name/path/db.sql.gz` | `AZURE_STORAGE_CONNECTION_STRING` or Azure Managed Identity |
| **Google Cloud**| `gs://bucket-name/path/db.sql.gz` | `GOOGLE_APPLICATION_CREDENTIALS` |

---

## 4. Multi-Channel Notifications Configuration

Configure webhook tokens via environment variables:

### Telegram
- Variable: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`
- Formats status with HTML tags (`<b>`, `•`), duration, backup ID, and byte size.

### Slack
- Variable: `SLACK_WEBHOOK_URL`
- Formats status with Slack Block Kit JSON header and markdown fields.

### Discord
- Variable: `DISCORD_WEBHOOK_URL`
- Formats status with Discord Embeds with Green (`0x2ECC71`) / Red (`0xE74C3C`) visual color coding.

---

## 5. Complete CLI Command Reference

### `test-connection`
```bash
db-backup test-connection [--profile <name>] [--db-type <mysql|postgresql>] [--host <host>] [--port <port>] [--user <user>] [--password <pass>] [--database <db>]
```

### `backup`
```bash
db-backup backup \
  --profile <profile-name> \
  --type <FULL|INCREMENTAL|DIFFERENTIAL> \
  --tables <table1,table2> \
  --encrypt <true|false> \
  --passphrase <encryption-passphrase> \
  --output <destination-uri> \
  --notify <telegram,slack,discord>
```

### `restore`
```bash
db-backup restore \
  --backup-id <backup-id> \
  --passphrase <encryption-passphrase> \
  [--profile <target-profile>]
```

### `history`
```bash
db-backup history [--profile <profile-name>] [--limit <number>]
```

### `daemon start`
```bash
db-backup daemon start [--config <path-to-schedule.yml>]
```
