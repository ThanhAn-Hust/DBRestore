# 🛡️ DBRestore (db-backup) - Database Backup & Selective Restore Tool

[![CI Verification](https://github.com/ThanhAn-Hust/DBRestore/actions/workflows/ci.yml/badge.svg)](https://github.com/ThanhAn-Hust/DBRestore/actions/workflows/ci.yml)
[![Docker Image](https://img.shields.io/badge/Docker-ghcr.io%2Fthanhan--hust%2Fdbrestore-blue?logo=docker)](https://github.com/ThanhAn-Hust/DBRestore/pkgs/container/dbrestore)
[![Java 21 LTS](https://img.shields.io/badge/Java-21%20LTS-orange?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

An enterprise-grade, memory-efficient **Database Backup & Point-in-Time Restore Engine** supporting **MySQL, MariaDB, and PostgreSQL** with multi-cloud storage sinks (AWS S3, Azure Blob, Google Cloud Storage, Local Disk), zero-buffering segmented AES-256-GCM encryption, SQLite WAL audit logging, chain-aware retention pruning, and daemon cron orchestration.

---

## 🎯 Công Dụng & Tính Năng Nổi Bật

1. **Zero-Buffering Streaming Pipeline**:
   - Dữ liệu dump từ database được stream trực tiếp qua tiến trình: `Process stdout` $\rightarrow$ Nén `GZIP` $\rightarrow$ Mã hóa `AES-256-GCM` $\rightarrow$ Đẩy thẳng lên Storage (AWS S3/Azure/GCS/Local).
   - **Không tạo file tạm ra đĩa cứng**, tiết kiệm dung lượng I/O và hỗ trợ backup database dung lượng hàng trăm GB mượt mà.
2. **Mã Hóa Phân Đoạn An Toàn (Segmented AES-256-GCM)**:
   - Dữ liệu được cắt khúc 64MB độc lập, mỗi segment có Authentication Tag 16-byte và vector khởi tạo (IV) riêng biệt derived từ Base IV.
   - Header 32-byte chuẩn `DBBK`, salt PBKDF2 100,000 vòng, có End-Of-Stream (EOS) marker chống tấn công cắt xén (Truncation Attack).
3. **Tự Động Phục Hồi Chuỗi (Restore Chain Resolution)**:
   - Khi khôi phục một bản sao lưu (`b-xxx`), hệ thống tự động tìm bản FULL gốc và toàn bộ các bản INCREMENTAL trung gian theo đúng thứ tự:
     $$\text{FULL BASE} \longrightarrow \text{INC 1} \longrightarrow \text{INC 2} \longrightarrow \text{TARGET}$$
   - Tự động nhận diện giải mã AES-GCM và giải nén Gzip trực tiếp vào `stdin` của database engine.
4. **Lưu Trữ Đa Đám Mây (Multi-Cloud Storage Sinks)**:
   - Tự động nhận diện giao thức qua URI: AWS S3 (`s3://`), Azure Blob Storage (`azure://`, `az://`), Google Cloud Storage (`gs://`, `gcs://`), Local/NFS (`file://`).
5. **Chính Sách Lưu Trữ Thông Minh (Chain-Aware Retention)**:
   - Hỗ trợ dọn dẹp theo `keep-last` hoặc `retention-days`.
   - **Bảo vệ toàn vẹn chuỗi**: Bản FULL gốc sẽ **không bao giờ bị xóa** nếu các bản INCREMENTAL/DIFFERENTIAL phụ thuộc vào nó vẫn còn hạn lưu trữ.
6. **Bảo Mật Tiến Trình Tuyệt Đối**:
   - Mật khẩu Database không bao giờ xuất hiện trên thanh command line arguments (Process table).
   - Được bảo vệ bằng biến môi trường (`MYSQL_PWD`, `PGPASSWORD`) hoặc file tạm `.my.cnf` gắn quyền POSIX `0600` / Windows User SID ACL, tự động thu dọn khi tắt ứng dụng hoặc qua `StartupCleanupSweep`.
7. **Lập Lịch Chạy Ngầm (Daemon Cron Scheduler)**:
   - Chạy nền 24/7 theo file `schedule.yml`.
   - Hỗ trợ chính sách chống xung đột (`on-overlap: SKIP | QUEUE | CANCEL_PREVIOUS`) với cơ chế khóa `ReentrantLock`.
8. **Thông Báo Đa Kênh (Multi-Channel Alerts)**:
   - Gửi cảnh báo trạng thái tự động qua **Telegram** (HTML), **Slack** (Block Kit), và **Discord** (Embeds màu sắc trực quan).

---

## ⚡ Hướng Dẫn Nhanh (Quick Start)

### Cách 1: Sử Dụng Với Docker (Khuyên Dùng)

```bash
# 1. Tải image từ GitHub Container Registry
docker pull ghcr.io/thanhan-hust/dbrestore:latest

# 2. Xem trợ giúp và danh sách lệnh
docker run --rm ghcr.io/thanhan-hust/dbrestore:latest help

# 3. Chạy backup MySQL có mã hóa và đẩy lên AWS S3
docker run --rm -it \
  -e AWS_ACCESS_KEY_ID="your-aws-key" \
  -e AWS_SECRET_ACCESS_KEY="your-aws-secret" \
  -e AWS_REGION="ap-southeast-1" \
  ghcr.io/thanhan-hust/dbrestore:latest backup \
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

### Cách 2: Sử Dụng Trực Tiếp File Java Fat JAR

```bash
# 1. Kiểm tra trợ giúp
java -jar target/db-backup-1.0.0-SNAPSHOT.jar help

# 2. Kiểm tra kết nối đến Database
java -jar target/db-backup-1.0.0-SNAPSHOT.jar test-connection --db-type postgresql --host localhost --port 5432 --user postgres --password secret --database testdb

# 3. Khôi phục chuỗi backup tự động
java -jar target/db-backup-1.0.0-SNAPSHOT.jar restore --backup-id "b-1723456789-abc1234" --passphrase "SecretAesPassphrase2026"
```

---

## 💻 Danh Sách Câu Lệnh & Kết Quả Mẫu (Commands & Outputs)

### 1. Lệnh Kiểm Tra Kết Nối: `test-connection`
- **Công dụng**: Kiểm tra xem thông tin đăng nhập, host, port và database có kết nối thành công hay không.
- **Cú pháp**:
  ```bash
  db-backup test-connection --db-type mysql --host 127.0.0.1 --port 3306 --user root --password "rootpass" --database shopdb
  ```
- **Kết quả trả về khi thành công**:
  ```
  Connection test SUCCESS for database 'shopdb' on 127.0.0.1:3306 (Engine: mysql)
  ```
- **Kết quả trả về khi thất bại**:
  ```
  Connection test FAILED for database 'shopdb': Access denied for user 'root'@'127.0.0.1' (using password: YES)
  ```

---

### 2. Lệnh Sao Lưu: `backup`
- **Công dụng**: Thực hiện sao lưu dữ liệu toàn phần (`FULL`) hoặc tăng dần (`INCREMENTAL`), nén Gzip, mã hóa AES-256 và stream lên nơi lưu trữ.
- **Cú pháp**:
  ```bash
  db-backup backup --profile prod-mysql --type FULL --encrypt true --passphrase "SecretAes2026" --output "s3://my-bucket/backups/prod.sql.gz" --notify "telegram,slack"
  ```
- **Kết quả trả về**:
  ```
  Backup SUCCESS! ID: b-1723456890-a1b2c3d4, Database: ecommerce_prod, Type: FULL, Size: 154.2 MB, Duration: 4120 ms, Destination: s3://my-bucket/backups/prod.sql.gz
  ```

---

### 3. Lệnh Khôi Phục: `restore`
- **Công dụng**: Tự động giải ngược chuỗi, tải từ S3/Storage về, giải mã AES, giải nén và nạp vào DB đích.
- **Cú pháp**:
  ```bash
  db-backup restore --backup-id "b-1723456890-a1b2c3d4" --passphrase "SecretAes2026" --profile staging-mysql
  ```
- **Kết quả trả về**:
  ```
  Restore SUCCESS! Successfully restored backup chain [b-1723420800-x9y8z7w6 -> b-1723456890-a1b2c3d4] for target backup b-1723456890-a1b2c3d4
  ```

---

### 4. Lệnh Tra Cứu Lịch Sử: `history`
- **Công dụng**: Xem danh sách các bản backup đã thực hiện từ SQLite Audit Log.
- **Cú pháp**:
  ```bash
  db-backup history --profile prod-mysql --limit 5
  ```
- **Kết quả trả về dạng bảng**:
  ```
  ID                     PROFILE      TYPE         STATUS     SIZE (B)     START TIME
  -------------------------------------------------------------------------------------------
  b-1723456890-a1b2c3d4  prod-mysql   INCREMENTAL  SUCCESS    15,420,112   2026-08-21 10:00:00
  b-1723453290-e5f6g7h8  prod-mysql   INCREMENTAL  SUCCESS    12,104,800   2026-08-21 09:00:00
  b-1723420800-x9y8z7w6  prod-mysql   FULL         SUCCESS    842,910,208  2026-08-21 02:00:00
  ```

---

### 5. Lệnh Chạy Daemon Ngầm: `daemon start`
- **Công dụng**: Khởi động daemon scheduler chạy nền theo cấu hình file `schedule.yml`.
- **Cú pháp**:
  ```bash
  db-backup daemon start --config ~/.db-backup/schedule.yml
  ```
- **Kết quả trả về**:
  ```
  Daemon started. Scheduled 2 job(s) from /root/.db-backup/schedule.yml
  ```

---

## 📚 Tài Liệu Hướng Dẫn Chi Tiết

- 📖 [**Cẩm Nang Người Dùng & Cấu Hình (USER_GUIDE.md)**](docs/USER_GUIDE.md): Chi tiết toàn bộ cờ CLI, cú pháp `config.yml`, `schedule.yml`, các kênh thông báo Telegram/Slack/Discord và Cloud Storage.
- 🐳 [**Hướng Dẫn Docker & Kubernetes (DOCKER_AND_K8S_GUIDE.md)**](docs/DOCKER_AND_K8S_GUIDE.md): Hướng dẫn chạy Docker, Docker Compose, Kubernetes Sidecar và Kubernetes CronJob.
- 🚨 [**Kịch Bản Cứu Hộ Dữ Liệu Khẩn Cấp (DISASTER_RECOVERY.md)**](docs/DISASTER_RECOVERY.md): Hướng dẫn chi tiết từng bước khôi phục dữ liệu khi gặp sự cố, ransomware hoặc data corruption.
- 📐 [**Tài Liệu Đặc Tả Thiết Kế (design-spec.md)**](docs/design-spec.md): Chi tiết kiến trúc hệ thống, format mã hoá 32-byte header và giải thuật xử lý luồng.

---

## 🛠️ Build Từ Mã Nguồn

```bash
# Clone repository
git clone https://github.com/ThanhAn-Hust/DBRestore.git
cd DBRestore

# Chạy 70 Unit Tests
mvn clean test

# Đóng gói Executable Fat JAR
mvn clean package -DskipTests

# Build Docker Image
docker build -t dbrestore:local .
```
