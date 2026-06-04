# BusBooking Web Backend

Du an IntelliJ IDEA chua backend Spring Boot va web admin cho he thong BusBooking.

Backend nay dam nhiem:

- Web admin quan ly dashboard, tuyen xe, xe, ghe, chuyen, ve, payment va nguoi dung.
- REST API cho app Android khach hang.
- REST API cho app Android staff.
- Tao URL/QR thanh toan VNPAY va xu ly callback/IPN.
- Ket noi MySQL chay bang XAMPP.

Android project nam tai:

```text
C:\Users\ADMIN\AndroidStudioProjects\BusBooking
```

## Cong nghe

- Java 21
- Spring Boot 3.3.5
- Spring Web
- Thymeleaf
- Spring Security
- Spring JDBC
- MySQL Connector/J
- ZXing QR
- XAMPP MySQL port `3306`

## Cau truc chinh

```text
src/main/java/com/example/busbooking/admin/
  config/       Cau hinh security, VNPAY
  controller/   Web controller va REST API
  model/        DTO/form/response model
  service/      Xu ly nghiep vu, database, QR, VNPAY

src/main/resources/
  templates/    Giao dien Thymeleaf web admin
  static/css/   CSS web admin
  application.yml
  schema.sql    Schema tu khoi tao khi Spring Boot start

database/
  busbooking_mysql.sql   File SQL day du de import bang phpMyAdmin/mysql CLI
```

## Chay local

1. Mo XAMPP va start MySQL port `3306`.

2. Import database neu can tao lai tu dau:

```text
database/busbooking_mysql.sql
```

3. Chay backend tu IntelliJ bang main class:

```text
com.example.busbooking.admin.AdminWebApplication
```

Hoac chay terminal:

```powershell
mvn spring-boot:run
```

4. Mo web admin:

```text
http://localhost:8081
```

## Tai khoan mac dinh

- Admin web: `busbooking.admin@gmail.com` / `123`
- User app: `0900000001` / `123`
- Staff app: `51B12345` / `123`

## API chinh

Mobile user:

- `POST /api/mobile/auth/register`
- `POST /api/mobile/auth/login`
- `GET /api/mobile/trips/search`
- `GET /api/mobile/trips/{id}/seats`
- `POST /api/mobile/tickets/book`
- `POST /api/mobile/tickets/book-batch`
- `GET /api/mobile/users/{id}/tickets`
- `GET /api/mobile/tickets/{id}`

Staff:

- `POST /api/staff/auth/login`
- `GET /api/staff/home`
- `GET /api/staff/trips`
- `GET /api/staff/trips/{tripId}`
- `POST /api/staff/tickets/verify`
- `POST /api/staff/tickets/{ticketId}/check-in`

VNPAY:

- `POST /api/payments/vnpay/create`
- `GET /api/payments/vnpay/return`
- `GET /api/payments/vnpay/ipn`

## Cau hinh moi truong

Mac dinh app dung database:

```text
jdbc:mysql://localhost:3306/busbooking
```

Co the ghi de bang bien moi truong:

```powershell
$env:BUSBOOKING_DB_URL="jdbc:mysql://localhost:3306/busbooking?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Bangkok&allowPublicKeyRetrieval=true"
$env:BUSBOOKING_DB_USERNAME="root"
$env:BUSBOOKING_DB_PASSWORD=""
```

VNPAY sandbox:

```powershell
$env:VNPAY_TMN_CODE="your_tmn_code"
$env:VNPAY_HASH_SECRET="your_hash_secret"
$env:VNPAY_RETURN_URL="http://10.0.2.2:8081/api/payments/vnpay/return"
```

Dung dien thoai that hoac IPN public thi expose backend bang ngrok/Cloudflare Tunnel va doi `VNPAY_RETURN_URL` sang URL public.
