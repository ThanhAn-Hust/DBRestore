# Cẩm Nang Người Dùng & Hướng Dẫn Cấu Hình (DBBackup User Guide)

Tài liệu này hướng dẫn chi tiết các cấu hình, toàn bộ danh mục câu lệnh CLI, định dạng mã hóa AES-GCM, kết nối Cloud Storage và thiết lập thông báo qua Webhook.

---

## 1. Cấu Hình Hồ Sơ Cơ Sở Dữ Liệu (`~/.db-backup/config.yml`)

Để không phải nhập lại host, port, username, password mỗi lần chạy lệnh, bạn có thể tạo các Profile trong file `~/.db-backup/config.yml`. File này tự động hỗ trợ đọc biến môi trường hệ thống định dạng `${ENV_VAR}` hoặc `${ENV_VAR:default}`.

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

## 2. Cấu Hình Lập Lịch Cron Ngầm (`~/.db-backup/schedule.yml`)

Daemon scheduler chạy ngầm sẽ đọc file `schedule.yml` và kích hoạt sao lưu theo biểu thức Cron (hỗ trợ cả chuẩn 5 phần hoặc 6 phần).

```yaml
jobs:
  - id: daily-mysql-full
    profile: mysql-prod
    cron: "0 0 2 * * *"               # Chạy lúc 02:00 sáng mỗi ngày
    backup-type: FULL
    single-transaction: true
    encrypt: true
    passphrase: ${BACKUP_PASSPHRASE}  # Mật khẩu mã hoá AES-GCM
    output: "s3://company-prod-backups/mysql/daily.sql.gz"
    on-overlap: SKIP                  # Tùy chọn: SKIP (bỏ qua), QUEUE (xếp hàng), CANCEL_PREVIOUS (hủy job trước)
    retention:
      keep-last: 7                    # Luôn giữ lại 7 chuỗi backup gần nhất
      retention-days: 30              # Xóa các chuỗi quá 30 ngày
    notifications:
      - telegram
      - slack

  - id: hourly-mysql-incremental
    profile: mysql-prod
    cron: "0 0 * * * *"               # Chạy mỗi đầu giờ
    backup-type: INCREMENTAL
    encrypt: true
    passphrase: ${BACKUP_PASSPHRASE}
    output: "s3://company-prod-backups/mysql/incremental.sql.gz"
    on-overlap: QUEUE
    notifications:
      - slack
```

---

## 3. Định Dạng Đường Dẫn Lưu Trữ (Storage URIs)

Hệ thống tự động nhận diện Storage Adapter qua tiền tố URI:

| Loại Lưu Trữ | Ví Dụ URI | Biến Môi Trường Cần Thiết |
|---|---|---|
| **Local / NFS** | `file:///var/backups/db.sql.gz` | Không cần |
| **AWS S3 / MinIO** | `s3://my-bucket/backups/db.sql.gz` | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION` |
| **Azure Blob Storage** | `azure://container-name/path/db.sql.gz` | `AZURE_STORAGE_CONNECTION_STRING` |
| **Google Cloud Storage**| `gs://bucket-name/path/db.sql.gz` | `GOOGLE_APPLICATION_CREDENTIALS` |

---

## 4. Cấu Hình Thông Báo Đa Kênh (Notifications)

Bạn chỉ cần truyền các biến môi trường sau cho hệ thống:

### 1. Telegram
- Biến môi trường: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`
- Định dạng: Tin nhắn HTML chứa trạng thái, tên database, dung lượng byte, thời gian thực thi và mã bản sao lưu.

### 2. Slack
- Biến môi trường: `SLACK_WEBHOOK_URL`
- Định dạng: Slack Block Kit JSON với Header và Section rõ ràng.

### 3. Discord
- Biến môi trường: `DISCORD_WEBHOOK_URL`
- Định dạng: Discord Embeds với màu Xanh lá (`0x2ECC71`) khi thành công và màu Đỏ (`0xE74C3C`) khi thất bại.

---

## 5. Chi Tiết Danh Mục Các Lệnh CLI

### 🔹 Lệnh 1: `test-connection`
Kiểm tra kết nối trực tiếp hoặc qua Profile:
```bash
db-backup test-connection --profile mysql-prod
# Hoặc truyền tham số trực tiếp:
db-backup test-connection --db-type postgresql --host localhost --port 5432 --user postgres --password "secret" --database my_db
```

### 🔹 Lệnh 2: `backup`
Thực hiện sao lưu:
- `--profile`: Tên profile trong `config.yml`.
- `--type`: `FULL` (toàn phần), `INCREMENTAL` (tăng dần), `DIFFERENTIAL` (khác biệt).
- `--tables`: Danh sách bảng phân tách bằng dấu phẩy (vd: `users,orders`).
- `--encrypt`: `true` hoặc `false`.
- `--passphrase`: Mật khẩu mã hoá AES-256-GCM.
- `--output`: Đường dẫn đích (`s3://...`, `azure://...`, `gs://...`, `file://...`).
- `--notify`: Kênh nhận thông báo (`telegram`, `slack`, `discord`).

Ví dụ:
```bash
db-backup backup \
  --profile mysql-prod \
  --type FULL \
  --encrypt true \
  --passphrase "MySecretPassphrase2026" \
  --output "s3://prod-backups/mysql/ecommerce.sql.gz" \
  --notify "telegram,slack"
```

### 🔹 Lệnh 3: `restore`
Khôi phục dữ liệu từ bản sao lưu:
- `--backup-id`: Mã ID của bản backup mục tiêu (vd: `b-1723456890-a1b2c3d4`).
- `--passphrase`: Mật khẩu giải mã nếu bản backup đã được mã hóa.
- `--profile`: Profile của database nhận dữ liệu khôi phục (nếu muốn restore sang DB khác).

Ví dụ:
```bash
db-backup restore --backup-id "b-1723456890-a1b2c3d4" --passphrase "MySecretPassphrase2026"
```

### 🔹 Lệnh 4: `history`
Xem lịch sử sao lưu đã lưu trong SQLite WAL:
```bash
db-backup history --profile mysql-prod --limit 10
```

### 🔹 Lệnh 5: `daemon start`
Khởi động background daemon chạy lập lịch:
```bash
db-backup daemon start --config ~/.db-backup/schedule.yml
```
