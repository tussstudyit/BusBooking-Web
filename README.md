# BusBooking IntelliJ Web - Admin + User

Du an IntelliJ IDEA chua backend Spring Boot va 2 web chinh cua he thong BusBooking:

- `Web Admin`: web quan tri cho ADMIN quan ly toan bo du lieu van hanh.
- `Web User`: web khach hang cho USER tim chuyen, chon ghe, dat ve, thanh toan va xem ve.

README nay tap trung mo ta nghiep vu cua 2 web trong project IntelliJ. Cac REST API mobile/staff/VNPAY duoc ghi o phan ho tro de giai thich cach web va app dung chung backend.

Project Android nam tai:

```text
C:\Users\ADMIN\AndroidStudioProjects\BusBooking
```

Ban tai lieu Android da duoc copy vao project IDEA de doc truc tiep:

```text
README_ANDROID.md
```

Khi mo IntelliJ IDEA tai project nay:

- Doc `README.md` de nam 2 web trong project IntelliJ: `Web Admin` va `Web User`.
- Doc `README_ANDROID.md` de nam 2 app Android: `App User` va `App Staff`.

## 1. Tong Quan 2 Web Trong IntelliJ

Project IntelliJ chay mot ung dung Spring Boot tai port `8081`, trong do co 2 web:

| Web | Actor | Namespace | Muc dich chinh |
|---|---|---|---|
| Web Admin | ADMIN | `/login`, `/dashboard`, `/routes`, `/buses`, `/trips`, `/users`, `/tickets`, `/payments` | Quan tri du lieu va van hanh nha xe |
| Web User | USER | `/user/**` | Khach hang tim chuyen, chon ghe, dat ve, thanh toan, xem QR ve |

He thong BusBooking co cac actor lien quan:

| Nhom | Kenh su dung | Vai tro |
|---|---|---|
| ADMIN | Web admin Spring Boot/Thymeleaf | Quan ly du lieu van hanh: tuyen, xe, ghe, chuyen, staff, ve, payment |
| USER | Web user va Android app khach hang | Dang ky, dang nhap, tim chuyen, chon ghe, dat ve, thanh toan, xem QR ve |
| STAFF | Android staff app | Xem chuyen duoc phan cong, xem hanh khach/ghe, quet QR, check-in |
| VNPAY | Cong thanh toan sandbox/public | Tao URL/QR thanh toan, callback/IPN cap nhat payment va ticket |

Backend nay dam nhiem:

- Web admin tai `/login`, `/dashboard`, `/routes`, `/buses`, `/trips`, `/users`, `/tickets`, `/payments`.
- Web user tai `/user/**`.
- REST API cho Android user tai `/api/mobile/**`.
- REST API cho Android staff tai `/api/staff/**`.
- REST API thanh toan VNPAY tai `/api/payments/vnpay/**`.
- Ket noi MySQL/XAMPP database `busbooking`.

## 2. Cong Nghe

- Java 21
- Spring Boot 3.3.5
- Spring Web MVC
- Thymeleaf
- Spring Security
- Spring JDBC
- MySQL Connector/J
- ZXing QR
- Maven
- MySQL/XAMPP port `3306`

## 3. Kien Truc Thu Muc

```text
src/main/java/com/example/busbooking/
  admin/
    AdminWebApplication.java
    config/
      SecurityConfig.java
    controller/
      AuthController.java
      DashboardController.java
      RouteAdminController.java
      BusAdminController.java
      TripAdminController.java
      UserAdminController.java
      TicketAdminController.java
      PaymentAdminController.java
      RollingTripController.java
    service/
      DashboardService.java
      RouteAdminService.java
      BusAdminService.java
      TripAdminService.java
      UserAdminService.java
      TicketAdminService.java
      PaymentAdminService.java
      RollingTripGeneratorService.java
      AdminUserDetailsService.java
    model/
      DTO, form va view model cho web admin

  user/
    controller/
      UserWebController.java
    service/
      UserAuthService.java
      UserWebService.java
      UserBookingService.java
      UserPaymentService.java
      UserLabelService.java
    model/
      UserWebModels.java

  api/
    user/
      MobileApiController.java
    staff/
      StaffApiController.java
    payment/
      VnpayPaymentController.java

  shared/
    service/
      RouteCatalogService.java
      QrCodeService.java
    payment/
      VnpayPaymentService.java
      VnpayProperties.java

src/main/resources/
  templates/
    login.html
    dashboard.html
    routes/
    buses/
    trips/
    users/
    tickets/
    payments/
    user/
  static/
    css/
  application.yml
  schema.sql

database/
  busbooking_mysql.sql
```

## 4. Database Va Bang Chinh

Database chinh: `busbooking`.

| Bang | Muc dich |
|---|---|
| `users` | Tai khoan ADMIN, USER, STAFF; mat khau BCrypt; trang thai khoa |
| `bus_companies` | Don vi/nha xe |
| `routes` | Tuyen xe, diem di, diem den, khoang cach, loai ghe 24/34, gia goi y, thoi luong |
| `buses` | Xe, bien so, ten xe, tong ghe, trang thai |
| `seats` | Ghe theo xe, tang, hang, cot, so ghe A/B |
| `trips` | Chuyen xe cu the theo tuyen, xe, gio di/den, gia, trang thai |
| `staff_profiles` | Ho so nhan vien va ma staff |
| `staff_bus_assignments` | Lien ket staff voi xe |
| `trip_staff_assignments` | Lien ket staff voi chuyen |
| `tickets` | Ve cua user theo trip/seat/payment |
| `trip_seats` | Trang thai ghe theo chuyen sau khi book/confirm/check-in |
| `payments` | Giao dich VNPAY |
| `payment_items` | Nhieu ve trong mot payment, dung cho dat nhieu ghe/khu hoi |
| `bookings`, `booking_tickets` | Gom nhom booking neu can mo rong |
| `ticket_checkins` | Lich su staff quet QR va check-in |

`schema.sql` tao/cap nhat cau truc bang va seed bo tai khoan khoi dau dung mot lan bang marker `initial_accounts_v1`. Du lieu quan ly khac khong bi seed lai; neu can DB mau day du thi import `database/busbooking_mysql.sql` hoac nap DB mau da co san.

### 4.1 Ghi Chu Sinh Du Lieu Runtime, VNPAY Va QR Ve

Day la 3 phan nen nam chac khi van dap backend trong project IntelliJ.

#### Sinh Du Lieu Chuyen Xe Runtime

File chinh:

```text
src/main/java/com/example/busbooking/admin/service/RollingTripGeneratorService.java
src/main/java/com/example/busbooking/admin/controller/RollingTripController.java
src/main/java/com/example/busbooking/admin/service/TripAdminService.java
src/main/resources/schema.sql
```

Y chinh:

- `schema.sql` chi seed tai khoan khoi dau mot lan. Bang `app_seed_history` ngan viec tao lai hoac ghi de tai khoan o cac lan khoi dong sau.
- Du lieu nen nhu `users`, `bus_companies`, `routes`, `buses`, `seats` khong duoc sinh lai boi rolling generator. Neu database moi trong thi can import `database/busbooking_mysql.sql` truoc.
- `RollingTripGeneratorService` chi insert cac chuyen mau con thieu trong cua so 5 ngay. Chuyen da ton tai khong bi cap nhat trang thai, gia, lich chay hay phan cong staff.
- So ngay sinh hien tai la `ROLLING_DAYS = 5`: tu ngay hom nay den 4 ngay tiep theo.
- `TripAdminService` cung gioi han admin xem/chon ngay bang `ADMIN_VISIBLE_DAYS = 5`.
- Template chuyen nam trong `TRIP_TEMPLATES`: gom `originId`, `destinationId`, `licensePlate`, `departMs`.
- Khi sinh chuyen, service tim route/bus co san theo:
  - `routes.origin_id`
  - `routes.destination_id`
  - `routes.seat_count = buses.total_seats`
  - `buses.license_plate`
  - route va bus phai active
- Neu chuyen theo route, bus, gio khoi hanh da ton tai thi khong insert trung.
- Neu chuyen moi duoc tao thi insert vao `trips` voi `status='SCHEDULED'`.
- Service chi tu dong gan staff ngay khi tao mot chuyen mau moi va staff con lich hop le.
- Service khong huy chuyen le template, khong seed lai du lieu nen va khong ghi de du lieu do admin quan ly.

Thoi diem chay:

| Cach chay | Noi goi | Y nghia |
|---|---|---|
| Khi app khoi dong | `@EventListener(ApplicationReadyEvent.class)` | Goi `generateUpcomingTripsIfStale()` |
| Hang ngay luc 00:05 | `@Scheduled(cron = "0 5 0 * * *")` | Lam moi rolling trips |
| Goi API thu cong | `POST /api/rolling-trips/refresh` | Admin/dev kich hoat tao lai rolling trips |

Bang bi tac dong:

| Bang | Tac dong |
|---|---|
| `trips` | Chi insert chuyen mau con thieu |
| `trip_staff_assignments` | Chi tu dong gan staff cho chuyen mau vua tao |

#### VNPAY Backend

File chinh:

```text
src/main/java/com/example/busbooking/shared/payment/VnpayPaymentService.java
src/main/java/com/example/busbooking/api/payment/VnpayPaymentController.java
src/main/java/com/example/busbooking/user/service/UserPaymentService.java
src/main/java/com/example/busbooking/user/service/UserBookingService.java
src/main/java/com/example/busbooking/admin/service/StartupMaintenanceService.java
src/main/resources/application.yml
```

Config:

```text
vnpay.pay-url
vnpay.tmn-code
vnpay.hash-secret
vnpay.return-url
```

Co the set bang bien moi truong:

```powershell
$env:VNPAY_PAY_URL="https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"
$env:VNPAY_TMN_CODE="your_tmn_code"
$env:VNPAY_HASH_SECRET="your_hash_secret"
$env:VNPAY_RETURN_URL="http://10.0.2.2:8081/api/payments/vnpay/return"
```

Luong xu ly:

1. User chon ghe/dat ve.
2. `UserBookingService` tao `tickets` co `status='PENDING_PAYMENT'`.
3. `UserBookingService` tao `payments` co `provider='VNPAY'`, `status='CREATED'`.
4. Web user vao `/user/payments/{paymentId}` hoac app goi `/api/payments/vnpay/create?paymentId=...`.
5. `VnpayPaymentService.createPaymentUrl` validate config, so tien va trang thai payment.
6. Payment het han sau 5 phut tinh tu `payments.created_at`.
7. Service tao tham so VNPAY, doi amount sang don vi VNPAY bang `amount * 100`.
8. Service ky du lieu bang `HmacSHA512` voi `VNPAY_HASH_SECRET`.
9. Service luu vao `payments`:
   - `status='PENDING'`
   - `vnp_txn_ref`
   - `payment_url`
   - `qr_content`
   - `qr_image_base64`
   - `qr_mime_type='image/png'`
   - `expires_at`
10. VNPAY redirect ve `/api/payments/vnpay/return` hoac goi IPN `/api/payments/vnpay/ipn`.
11. Backend verify `vnp_SecureHash` va so tien.
12. Neu thanh cong:
   - `payments.status='SUCCESS'`
   - `tickets.status='CONFIRMED'`
   - upsert `trip_seats.status='CONFIRMED'`
13. Neu that bai:
   - `payments.status='FAILED'`
   - `tickets.status='PAYMENT_FAILED'`
14. Neu qua 5 phut:
   - `payments.status='EXPIRED'`
   - `tickets.status='PAYMENT_FAILED'`

Note van dap:

- QR VNPAY la QR thanh toan, noi dung la `payment_url` VNPAY.
- QR VNPAY duoc luu trong bang `payments`, cot `qr_image_base64`.
- `/api/payments/vnpay/return` tra HTML co deeplink `busbooking://payment-return?paymentId=...` de app Android co the quay lai app, dong thoi co link quay ve web `/user/payments/{paymentId}`.
- `StartupMaintenanceService` chay khi app ready de reconcile ticket neu `payments.status='SUCCESS'` ma ticket van con `PENDING/PENDING_PAYMENT`.

#### QR Code Ve

File chinh:

```text
src/main/java/com/example/busbooking/shared/service/QrCodeService.java
src/main/java/com/example/busbooking/user/service/UserWebService.java
src/main/java/com/example/busbooking/api/staff/StaffApiController.java
src/main/resources/templates/user/ticket-detail.html
```

Y chinh:

- QR ve khac QR VNPAY.
- QR ve chi hien sau khi ticket da thanh toan thanh cong: `CONFIRMED` hoac da check-in `CHECKED_IN`.
- Noi dung QR ve co format:

```text
BUSBOOKING-TICKET:<ticketId>
```

- `UserWebService.mapTicket` tao `qrContent` bang `ticketQrContent(ticketId)`.
- `QrCodeService.createPngBase64(qrContent)` tao anh PNG base64, mac dinh size `360x360`.
- Web user hien QR ve tai `/user/tickets/{ticketId}` trong `ticket-detail.html`.
- App user cung nhan QR ve qua API chi tiet ticket neu ticket da paid.
- App staff quet QR va goi `/api/staff/tickets/verify` de xac minh.
- Staff check-in thanh cong se cap nhat:
  - `tickets.status='CHECKED_IN'`
  - `trip_seats.status='CHECKED_IN'`
  - insert/update `ticket_checkins` voi method `QR`

Tom tat de tra loi nhanh:

| Noi dung | QR VNPAY | QR ve |
|---|---|---|
| Muc dich | Thanh toan | Soat ve len xe |
| Noi dung QR | URL thanh toan VNPAY | `BUSBOOKING-TICKET:<ticketId>` |
| Tao khi nao | Khi tao payment URL | Khi ticket da paid |
| Luu/hien thi | Bang `payments`, trang payment | Ticket view/API ticket |
| Nguoi dung | USER thanh toan | STAFF quet check-in |

## 5. Nghiep Vu 2 Web

### 5.1 Web Admin - Quan Tri He Thong

Admin dang nhap qua Spring Security bang tai khoan `role='ADMIN'`, `is_blocked=false`.

Chuc nang chinh:

| Nhom chuc nang | Route | Xu ly chinh |
|---|---|---|
| Dang nhap/dang xuat | `/login`, `/logout` | Xac thuc admin bang `AdminUserDetailsService` va BCrypt |
| Dashboard | `/dashboard` | Thong ke tong hop ve user, route, bus, trip, ticket, payment |
| Quan ly tuyen | `/routes` | Them/sua/bat tat tuyen, chon tinh/thanh, seat_count 24/34, gia goi y |
| Quan ly xe | `/buses` | Them/sua/bat tat xe, tong ghe 24/34, bien so, tao ghe tu dong |
| So do ghe xe | `/buses/{id}/seats` | Xem ghe theo tang, hang, cot; generate lai ghe |
| Quan ly chuyen | `/trips` | Them/sua/huy chuyen, gan xe, gan staff, loc theo ngay |
| Quan ly user/staff | `/users` | Tim user, tao tai khoan STAFF bang email Gmail, khoa/mo khoa tai khoan |
| Quan ly ve | `/tickets` | Xem ve, trang thai ve, thong tin hanh khach |
| Quan ly payment | `/payments` | Xem giao dich VNPAY va trang thai thanh toan |
| Rolling trips | `/api/rolling-trips/refresh` | Tu dong tao chuyen theo template co san |

Quy tac nghiep vu admin:

- Xe chi ho tro `24` hoac `34` ghe. Gia tri khac se ve mac dinh `34`.
- Khi tao xe, `BusAdminService` tao ghe vao bang `seats`.
- Ghe chia 2 tang: tang 1 prefix `A`, tang 2 prefix `B`.
- Loai 34 ghe: moi tang 17 ghe, grid 3 cot, co o trong de tao loi di.
- Loai 24 ghe: moi tang 12 ghe, su dung cot 0 va 2 de tao loi giua.
- Khi tao/sua chuyen, `TripAdminService` kiem tra xe va tuyen phai cung loai ghe 24/34.
- Xe duoc chon phai phu hop diem phuc vu cua tuyen theo `BusRouteMatcher`.
- Staff duoc gan chuyen khong duoc trung gio, khong qua sat gio, toi da 2 chuyen/ngay.
- Huy chuyen se set `trips.status='CANCELLED'` va inactive assignment staff cua chuyen do.

### 5.2 Web User - Khach Hang Dat Ve

Web user nam duoi namespace `/user/**`, tach rieng voi web admin.

Route chinh:

| Route | Muc dich | Can dang nhap |
|---|---|---|
| `/user/login` | Dang nhap user bang so dien thoai | Khong |
| `/user/register` | Dang ky user moi | Khong |
| `/user/logout` | Xoa session user | Co |
| `/user/home` | Trang chu, tim ve, tuyen pho bien, ve sap toi | Khong bat buoc |
| `/user/routes` | Danh sach tuyen xe | Khong bat buoc |
| `/user/trips` | Tim chuyen theo diem di, diem den, ngay, loai ghe | Khong bat buoc |
| `/user/trips/{tripId}` | Chi tiet chuyen | Khong bat buoc |
| `/user/trips/{tripId}/seats` | Chon ghe | Co |
| `/user/trips/{tripId}/seats/book` | Tao ticket va payment | Co |
| `/user/payments/{paymentId}` | Hien QR/link VNPAY | Co |
| `/user/payments/{paymentId}/status` | Poll trang thai payment | Co |
| `/user/payments/{paymentId}/cancel` | Huy payment dang cho | Co |
| `/user/tickets` | Ve cua toi | Co |
| `/user/tickets/{ticketId}` | Chi tiet ve va QR ve | Co |
| `/user/tickets/{ticketId}/cancel` | Huy ve chua thanh toan | Co |
| `/user/profile` | Ho so ca nhan | Co |

Session web user:

- Controller luu session rieng bang key `WEB_USER_ID`.
- Khong dung chung Spring Security session cua admin.
- Moi route can dang nhap se doc `WEB_USER_ID`, sau do load lai user bang `UserAuthService`.
- User hop le phai co `role='USER'`, khong bi khoa.

Quy tac nghiep vu user:

- Dang ky tao user `role='USER'`, mat khau BCrypt, khong cho trung phone/email.
- Dang nhap bang phone/password.
- Tim chuyen chi lay `trips.status='SCHEDULED'`, gio di lon hon hien tai, dung ngay tim, xe active va con ghe.
- Danh sach ghe lay tu database, khong hardcode so ghe o frontend.
- Ghe bi xem la da dat neu ticket/trip_seat co trang thai `PENDING_PAYMENT`, `PENDING`, `CONFIRMED`, `CHECKED_IN`.
- Khi dat ve, backend kiem tra user, trip, ghe thuoc bus cua trip va ghe con trong.
- Ticket moi co `status='PENDING_PAYMENT'`.
- Payment moi co `provider='VNPAY'`, `status='CREATED'`.
- User chi huy duoc ve chua thanh toan thanh cong: `PENDING`, `PENDING_PAYMENT`, `PAYMENT_FAILED`, `FAILED`, `EXPIRED`.
- Ve da `CONFIRMED` hoac `CHECKED_IN` moi sinh QR noi dung `BUSBOOKING-TICKET:<ticketId>`.

## 6. API Ho Tro App Android

Phan nay khong phai web trong IntelliJ, nhung la API backend dung chung cho Android va cong thanh toan.

### 6.1 API App User Android

`MobileApiController` cung cap API cho app khach hang:

| API | Muc dich |
|---|---|
| `POST /api/mobile/auth/register` | Dang ky user |
| `POST /api/mobile/auth/login` | Dang nhap user |
| `GET /api/mobile/users/{id}` | Lay ho so user |
| `PUT /api/mobile/users/{id}` | Cap nhat ho so user |
| `GET /api/mobile/routes/origins` | Danh sach diem di |
| `GET /api/mobile/routes/destinations` | Danh sach diem den |
| `GET /api/mobile/routes/search?q=` | Tim tuyen |
| `GET /api/mobile/trips/search` | Tim chuyen theo diem, ngay, loai ghe |
| `GET /api/mobile/trips/{id}` | Chi tiet chuyen |
| `GET /api/mobile/trips/{id}/availability` | Tong ghe va ghe da dat |
| `GET /api/mobile/trips/{id}/seats` | So do ghe theo trip |
| `POST /api/mobile/tickets/book` | Dat 1 ghe |
| `POST /api/mobile/tickets/book-batch` | Dat nhieu ghe/nhieu chang, dung cho khu hoi |
| `GET /api/mobile/tickets/{id}` | Chi tiet ve |
| `GET /api/mobile/users/{id}/tickets` | Danh sach ve cua user |
| `POST /api/mobile/tickets/{id}/cancel` | Huy ve neu hop le |

### 6.2 API App Staff Android

`StaffApiController` cung cap API cho staff app:

| API | Muc dich |
|---|---|
| `POST /api/staff/auth/login` | Dang nhap staff bang email Gmail va password |
| `GET /api/staff/home?staffId=` | Tong quan chuyen trong ngay/gan nhat |
| `GET /api/staff/trips?staffId=` | Danh sach chuyen duoc phan cong |
| `GET /api/staff/trips/{tripId}?staffId=` | Chi tiet chuyen, hanh khach, so do ghe |
| `POST /api/staff/tickets/verify` | Quet/xac minh QR ve |
| `POST /api/staff/tickets/{ticketId}/check-in` | Check-in hanh khach |

Quy tac staff:

- Staff app chi chap nhan user `role='STAFF'`, `is_blocked=false`.
- Staff dang nhap bang email Gmail trong bang `users`, khong phai bien so xe.
- Staff chi xem duoc chuyen co `trip_staff_assignments.status='ACTIVE'`.
- QR hop le co the la `BUSBOOKING-TICKET:<ticketId>`, `TICKET:<id>`, query `ticketId=...`, hoac payment ref co the tra ve ticket.
- Ve hop le de check-in phai co status `CONFIRMED` hoac `CHECKED_IN`.
- Check-in cap nhat:
  - `tickets.status='CHECKED_IN'`
  - `trip_seats.status='CHECKED_IN'`
  - insert/update `ticket_checkins` voi method `QR`

### 6.3 API VNPAY - Thanh Toan Va QR

API:

| API | Muc dich |
|---|---|
| `POST /api/payments/vnpay/create?paymentId=` | Tao payment URL, QR VNPAY |
| `POST /api/payments/vnpay/cancel?paymentId=` | Huy payment dang cho |
| `GET /api/payments/vnpay/return` | Callback tra ve app/web sau thanh toan |
| `GET /api/payments/vnpay/ipn` | IPN tu VNPAY |

Luong payment:

1. User chon ghe va dat ve.
2. `UserBookingService` tao `tickets` va `payments`.
3. App/web goi `/api/payments/vnpay/create`.
4. `VnpayPaymentService` kiem tra config, amount, timeout 5 phut, tao URL ky HMAC-SHA512.
5. Backend tao anh QR base64 tu payment URL bang ZXing.
6. User thanh toan tren VNPAY.
7. VNPAY callback ve `/api/payments/vnpay/return` hoac `/ipn`.
8. Backend verify checksum va amount.
9. Neu thanh cong:
   - `payments.status='SUCCESS'`
   - `tickets.status='CONFIRMED'`
   - `trip_seats.status='CONFIRMED'`
10. Neu that bai:
   - `payments.status='FAILED'`
   - `tickets.status='PAYMENT_FAILED'`
11. Neu qua 5 phut:
   - `payments.status='EXPIRED'`
   - `tickets.status='PAYMENT_FAILED'`

## 7. Trang Thai Quan Trong

Ticket:

| Status | Y nghia |
|---|---|
| `PENDING_PAYMENT` | Da tao ve, dang cho thanh toan |
| `PENDING` | Trang thai cho cu/tuong thich |
| `CONFIRMED` | Thanh toan thanh cong, ve hop le |
| `CHECKED_IN` | Staff da xac nhan len xe |
| `PAYMENT_FAILED` | Thanh toan that bai/het han |
| `CANCELLED` | User/admin da huy |

Payment:

| Status | Y nghia |
|---|---|
| `CREATED` | Moi tao payment, chua tao link VNPAY |
| `PENDING` | Da tao URL/QR VNPAY, dang cho thanh toan |
| `SUCCESS` | VNPAY xac nhan thanh cong |
| `FAILED` | VNPAY tra ve that bai |
| `CANCELLED` | User huy payment |
| `EXPIRED` | Qua thoi gian thanh toan |

Trip:

| Status | Y nghia |
|---|---|
| `SCHEDULED` | Dang mo ban/cho khoi hanh |
| `RUNNING` | Dang chay |
| `COMPLETED` | Da hoan thanh |
| `CANCELLED` | Da huy |

## 8. Cach Chay Local

1. Mo XAMPP va start MySQL port `3306`.

2. Import database neu can tao lai:

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

5. Mo web user:

```text
http://localhost:8081/user/home
```

## 9. Cau Hinh Moi Truong

Mac dinh:

```text
jdbc:mysql://localhost:3306/busbooking
```

Ghi de database:

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

Dung dien thoai that hoac can IPN public thi expose backend bang ngrok/Cloudflare Tunnel va doi `VNPAY_RETURN_URL` sang URL public.

## 10. Tai Khoan Su Dung

Tai khoan khoi dau duoc tao mot lan khi `schema.sql` chay lan dau:

- Admin web: `busbooking.admin@gmail.com` / `123`
- User app: `0900000001` / `123`
- Staff app: `busbooking.staff@gmail.com` / `123`

Staff khoi dau co `staff_code='STAFF001'`; staff app dang nhap bang email Gmail va password.

Cach tao staff:

1. Dang nhap web admin.
2. Vao `/users`.
3. Tao staff voi email Gmail, phone va password.
4. Gan staff vao chuyen trong form tao/sua trip.
5. Staff app dang nhap bang email Gmail vua tao.

## 11. Lien Ket Voi Android

Android project goi backend qua:

```properties
api.baseUrl=http://10.0.2.2:8081
staff.api.baseUrl=http://10.0.2.2:8081
```

- Emulator Android dung `10.0.2.2` de tro ve may host.
- Dien thoai that dung IP LAN cua may chay backend, vi du `http://192.168.1.199:8081`.
- App khach hang dung `/api/mobile/**` va `/api/payments/vnpay/**`.
- App staff dung `/api/staff/**`.

## 12. Luong Nghiep Vu 2 Web

### Web Admin Tao Du Lieu Van Hanh

```text
ADMIN -> dang nhap /login
      -> tao/cap nhat tuyen o /routes
      -> tao xe va generate ghe o /buses
      -> tao staff o /users
      -> tao chuyen va gan staff o /trips
      -> theo doi ticket/payment o /tickets va /payments
```

### Web User Dat Ve Mot Chieu

```text
USER -> tim chuyen -> xem chi tiet -> load ghe -> chon ghe
     -> POST /api/mobile/tickets/book-batch
     -> tao tickets PENDING_PAYMENT + payment CREATED
     -> tao QR/link VNPAY
     -> VNPAY callback
     -> payment SUCCESS + ticket CONFIRMED
     -> user xem QR ve BUSBOOKING-TICKET:<ticketId>
     -> staff quet QR va check-in
```

### Web User Dat Ve Khu Hoi

```text
USER -> chon chuyen di -> chon ghe chieu di
     -> tim chuyen ve -> chon ghe chieu ve
     -> POST /api/mobile/tickets/book-batch voi 2 segments
     -> tao nhieu tickets chung paymentId
     -> thanh toan 1 lan cho tong tien
```

### Sau Thanh Toan

```text
VNPAY callback -> backend xac minh checksum
              -> payment SUCCESS
              -> ticket CONFIRMED
              -> Web User hien QR ve
```

## 13. Cau Hoi Van Dap Nhanh

**He thong co nhung actor nao?**
Co 3 actor nguoi dung: ADMIN, USER, STAFF; va 1 actor ngoai la VNPAY.

**Tai sao web admin va web user khong xung dot dang nhap?**
Admin dung Spring Security session. Web user dung `HttpSession` rieng voi key `WEB_USER_ID`.

**App Android co ket noi truc tiep MySQL khong?**
Khong. App goi REST API Spring Boot, backend moi ket noi MySQL.

**Tai sao khong hardcode ghe o app?**
Vi ghe lay tu bang `seats` theo `bus_id` cua trip, gom `floor`, `row_index`, `column_index`. App/web chi render theo du lieu backend.

**Lam sao biet ghe da ban?**
Backend kiem tra `tickets` va `trip_seats` voi status `PENDING_PAYMENT`, `PENDING`, `CONFIRMED`, `CHECKED_IN`.

**Vi sao dat ve can transaction?**
De tranh truong hop tao mot phan ticket/payment roi loi giua chung; `UserBookingService.bookSegments` dung `@Transactional`.

**Khi thanh toan VNPAY thanh cong thi cap nhat gi?**
`payments.status='SUCCESS'`, `tickets.status='CONFIRMED'`, va `trip_seats.status='CONFIRMED'`.

**Staff app dang nhap bang gi?**
Bang email Gmail va password cua tai khoan `role='STAFF'`.

**Staff co xem duoc moi chuyen khong?**
Khong. Staff chi xem chuyen co record active trong `trip_staff_assignments`.

**QR ve khac QR VNPAY the nao?**
QR VNPAY dung de thanh toan. QR ve co noi dung `BUSBOOKING-TICKET:<ticketId>` dung de staff quet len xe.

**Tai sao co `payment_items`?**
De mot payment co the gom nhieu ticket, phuc vu dat nhieu ghe hoac dat khu hoi.

**Neu payment qua han thi sao?**
Sau 5 phut, payment bi set `EXPIRED`, ticket lien quan set `PAYMENT_FAILED`.

## 14. Kiem Thu Hien Co

Test trong backend:

```text
src/test/java/com/example/busbooking/
  admin/util/BusRouteMatcherTest.java
  shared/service/RouteCatalogServiceTest.java
  user/service/UserAuthServiceTest.java
  user/service/UserWebServiceRouteCardTest.java
```

Chay test:

```powershell
mvn test
```
