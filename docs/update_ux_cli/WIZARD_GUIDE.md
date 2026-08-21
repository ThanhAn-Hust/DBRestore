# Interactive CLI Wizard Guide (Cẩm Nang Hướng Dẫn Wizard Tương Tác)

Công cụ `db-backup` (DBRestore) cung cấp chế độ **Interactive Wizard** trực quan dành cho người dùng muốn thao tác nhanh thông qua menu số `[1, 2, 3, 4]` mà không cần nhớ cú pháp câu lệnh dài dòng.

---

## 🚀 Cách Khởi Động Wizard

Người dùng chỉ cần gõ lệnh trực tiếp không kèm tham số:

```bash
# Sử dụng với file JAR:
java -jar db-backup.jar

# Hoặc sử dụng qua Docker:
docker run -it --rm -v ~/.db-backup:/root/.db-backup ghcr.io/thanhan-hust/dbrestore:latest
```

---

## 🖥️ Giao Diện Menu Chính (Main Menu)

Khi khởi động, màn hình chào mừng song ngữ sẽ xuất hiện:

```text
=====================================================
  🛡️  DBRESTORE - DATABASE BACKUP & RESTORE TOOL
=====================================================
Welcome to DBRestore CLI!

Please choose an action:
  [1] Test Database Connection
  [2] Backup Database
  [3] Restore Database
  [4] View Backup Audit History
  [5] Manage Profiles & Cloud Credentials
  [6] Change Language (English / Tiếng Việt)
  [0] Exit

Select an option [0-6] [1]: 
```

---

## 🛠️ Chi Tiết 6 Chức Năng Wizard

### 1. Test Database Connection (Kiểm Tra Kết Nối)
- Cho phép chọn từ danh sách Profile có sẵn trong `~/.db-backup/config.yml` hoặc nhập thông số DB tùy chỉnh (`Host`, `Port`, `User`, `Password`, `Database`).
- Tự động kiểm tra và trả về thông báo trạng thái tức thì.

### 2. Backup Database (Sao Lưu Cơ Sở Dữ Liệu)
1. **Nguồn dữ liệu**: Chọn Profile hoặc nhập thủ công.
2. **Loại sao lưu**: `1: FULL`, `2: INCREMENTAL`, `3: DIFFERENTIAL`.
3. **Bảng sao lưu**: Nhập danh sách bảng hoặc Enter để sao lưu toàn bộ.
4. **Nơi lưu trữ**:
   - `[1] AWS S3`: Nhập S3 Bucket & Key.
   - `[2] Azure Blob`: Nhập Azure Container & Path.
   - `[3] Google Cloud`: Nhập GCS Bucket & Path.
   - `[4] Local Disk`: Nhập đường dẫn file trên máy.
5. **Mã hóa AES-256-GCM**: Hỏi mật khẩu mã hóa (ẩn ký tự và yêu cầu xác nhận 2 lần).
6. **Kênh thông báo**: Nhập `telegram,slack,discord` nếu muốn nhận alert.
7. **Bảng tổng kết & Xác nhận**: Hiển thị tóm tắt trước khi kích hoạt stream.

### 3. Restore Database (Phục Hồi Dữ Liệu)
- Tự động truy vấn SQLite Audit Log và hiển thị danh sách các bản backup gần nhất được đánh số `[1]`, `[2]`, `[3]`...
- Người dùng chỉ cần gõ số thứ tự của bản backup cần khôi phục.
- Nhập mật khẩu giải mã (nếu có).
- Chọn khôi phục đè vào DB gốc hoặc chọn DB đích mới.
- Cảnh báo an toàn trước khi thực hiện ghi đè.

### 4. View Backup Audit History (Tra Cứu Lịch Sử)
- Hiển thị bảng định dạng danh sách các lần sao lưu: `ID`, `DB NAME`, `TYPE`, `STATUS`, `SIZE`, `START TIME`.

### 5. Manage Profiles & Cloud Credentials (Quản Lý Cấu Hình)
- Liệt kê toàn bộ các Profile kết nối đã lưu trong `~/.db-backup/config.yml`.

### 6. Change Language (Đổi Ngôn Ngữ)
- Chuyển đổi qua lại giữa **English** và **Tiếng Việt**.
- Lựa chọn ngôn ngữ được lưu tự động vào `~/.db-backup/preferences.json` để ghi nhớ cho các lần chạy sau.
