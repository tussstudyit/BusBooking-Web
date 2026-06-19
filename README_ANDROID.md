# BusBooking Android - App User + App Staff

Du an Android Studio gom 2 app di dong dung chung backend Spring Boot BusBooking:

- `App User` (`app`): ung dung khach hang dat ve xe.
- `App Staff` (`staff-app`): ung dung nhan vien nha xe kiem ve va theo doi chuyen duoc phan cong.

README nay tap trung mo ta nghiep vu, cau truc va luong man hinh cua 2 app Android. Backend/web chi duoc nhac den de giai thich API ket noi.

Backend Spring Boot va web admin nam tai:

```text
C:\Users\ADMIN\IdeaProjects\BusBooking
```

## 1. Tong Quan 2 App Android

Android project khong ket noi truc tiep MySQL. Ca 2 app deu goi REST API tu backend Spring Boot:

| App | Module | Actor | API backend | Muc dich |
|---|---|---|---|---|
| App User | `app` | USER | `/api/mobile/**`, `/api/payments/vnpay/**` | Tim chuyen, chon ghe, dat ve, thanh toan, xem QR ve |
| App Staff | `staff-app` | STAFF | `/api/staff/**` | Xem chuyen duoc phan cong, xem ghe/hanh khach, quet QR, check-in |

Du lieu nghiep vu chinh nam trong MySQL cua backend. Android chi luu session/token cuc bo va cau hinh base URL.

## 2. Cong Nghe

- Kotlin
- Android XML layout
- MVVM voi ViewModel, LiveData, Repository
- Navigation Component
- ViewBinding
- Material Components
- Kotlin Coroutines
- Kotlinx Serialization
- DataStore/SharedPreferences cho session/cau hinh
- `HttpURLConnection` de goi REST API
- ZXing Android Embedded cho staff app quet QR
- Gradle Kotlin DSL

## 3. Cau Truc Thu Muc

```text
BusBooking/
  settings.gradle.kts
  build.gradle.kts
  local.properties
  database/
    busbooking_mysql.sql

  app/
    build.gradle.kts
    src/main/
      AndroidManifest.xml
      java/com/example/busbooking/
        MainActivity.kt
        data/
          entity/
          model/
          relations/
          session/
        domain/
          models/
          repository/
        presentation/
          adapter/
          ui/
          ui/state/
          viewmodel/
        utils/
      res/
        layout/
        navigation/nav_graph.xml
        menu/bottom_nav_menu.xml
        drawable/
        values/

  staff-app/
    build.gradle.kts
    src/main/
      AndroidManifest.xml
      java/com/example/busbooking/staff/
        StaffMainActivity.kt
        data/
          api/
          model/
          repository/
        session/
        ui/
          home/
          login/
          profile/
          ticket/
          trip/
        utils/
      res/
        layout/
        navigation/staff_nav_graph.xml
        menu/staff_bottom_nav.xml
        drawable/
        values/
```

## 4. App User (`app`) - Ung Dung Khach Hang

### 4.1 Man Hinh Va Navigation

Navigation graph: `app/src/main/res/navigation/nav_graph.xml`.

| Man hinh | Fragment | Layout | Chuc nang |
|---|---|---|---|
| Splash | `SplashFragment` | `fragment_splash.xml` | Kiem tra session, dieu huong login/home |
| Dang nhap | `LoginFragment` | `fragment_login.xml` | Dang nhap bang so dien thoai/password |
| Dang ky | `RegisterFragment` | `fragment_register.xml` | Tao tai khoan USER moi |
| Trang chu | `HomeFragment` | `fragment_home.xml` | Banner, uu dai, ve sap toi, form tim chuyen |
| Chon diem | `LocationPickerBottomSheet` | `fragment_location_picker_bottom_sheet.xml` | Chon diem di/diem den |
| Tuyen xe | `RouteSearchFragment` | `fragment_route_search.xml` | Tim va hien danh sach tuyen |
| Danh sach chuyen | `TripListFragment` | `fragment_trip_list.xml` | Loc ngay, loai xe 24/34, chon chuyen |
| Chi tiet chuyen | `TripDetailsFragment` | `fragment_trip_details.xml` | Gia, gio, route, xe, nut chon ghe |
| Chon ghe | `SeatSelectionFragment` | `fragment_seat_selection.xml` | Render ghe 2 tang, chon nhieu ghe, tinh tien |
| Xac nhan dat ve | `BookingConfirmationFragment` | `fragment_booking_confirmation.xml` | Hien QR VNPAY, mo link thanh toan, poll trang thai |
| Ve cua toi | `MyTicketsFragment` | `fragment_my_tickets.xml` | Ve sap toi va ve lich su |
| Lich su dat ve | `BookingHistoryFragment` | `fragment_booking_history.xml` | Danh sach ve lich su |
| Chi tiet ve | `TicketDetailsFragment` | `fragment_ticket_details.xml` | Thong tin ve, QR ve neu da thanh toan |
| Ho so | `UserProfileFragment` | `fragment_user_profile.xml` | Xem/cap nhat ten, email, phone |

### 4.2 Lop Chinh Trong `app`

| Nhom | File | Vai tro |
|---|---|---|
| Activity | `MainActivity.kt` | Host NavController va bottom navigation |
| Session | `utils/SessionManager.kt`, `data/session/SessionManager.kt` | Luu user dang nhap |
| API config | `domain/repository/ServerConfig.kt` | Lay base URL tu BuildConfig/local storage, uu tien emulator |
| HTTP client | `domain/repository/ApiClient.kt` | GET/POST/PUT bang `HttpURLConnection`, parse JSON |
| Auth | `AuthRepository.kt`, `AuthViewModel.kt` | Dang ky, dang nhap, ho so |
| Route | `RouteRepository.kt`, `RouteSearchViewModel.kt` | Lay diem di/den, tim tuyen |
| Trip | `TripRepository.kt`, `TripListViewModel.kt`, `TripDetailsViewModel.kt` | Tim chuyen, chi tiet, so ghe con |
| Seat | `ApiSeatRepository.kt`, `SeatSelectionViewModel.kt`, `SeatAdapter.kt` | Load ghe, chon ghe, book ghe |
| Ticket | `TicketRepository.kt`, `ApiTicketRepository.kt`, `UserViewModel.kt` | Ve cua toi, chi tiet ve, huy ve |
| Payment | `VnpayRepository.kt`, `BookingConfirmationViewModel.kt` | Tao QR/link VNPAY, huy payment, tao lai QR |
| Format/status | `StatusLabels.kt`, `UiState.kt` | Hien label trang thai tieng Viet |

### 4.3 Nghiep Vu App User

Dang ky/dang nhap:

- Dang ky goi `POST /api/mobile/auth/register`.
- Dang nhap goi `POST /api/mobile/auth/login`.
- User hop le co `role='USER'`, khong bi khoa o backend.
- Sau login, app luu user vao `SessionManager`.

Trang chu:

- Load user hien tai.
- Load ve sap toi de nhac lich.
- Load diem di/diem den tu API.
- Hien banner va promotion local.

Tim chuyen:

- User chon diem di, diem den, ngay di.
- Co filter loai xe `24 ghe`, `34 ghe` hoac tat ca.
- Goi `GET /api/mobile/trips/search?origin=&destination=&tripDate=&totalSeats=`.
- Chi hien chuyen backend tra ve con hop le.

Chon ghe:

- Goi `GET /api/mobile/trips/{id}/seats`.
- App tach ghe theo `floor=1` va `floor=2`.
- `SeatAdapter` render bang `row_index`, `column_index`; o trong duoc render invisible de giu layout.
- Ghe `booked=true` khong cho chon.
- User co the chon nhieu ghe.
- Tong tien = so ghe chon x gia chuyen.

Dat ve mot chieu:

```text
Chon chuyen -> Chon ghe -> bookSeats()
-> ApiSeatRepository.reserveSeats()
-> POST /api/mobile/tickets/book-batch
-> backend tao tickets + payment
-> VnpayRepository.createPaymentPayload()
-> hien QR/link VNPAY
```

Dat ve khu hoi:

```text
Chon chieu di -> Chon ghe chieu di
-> Tim chieu ve voi origin/destination dao nguoc
-> Chon ghe chieu ve
-> POST /api/mobile/tickets/book-batch voi 2 segment
-> nhieu ticket chung 1 paymentId
-> thanh toan 1 lan tong tien
```

Thanh toan:

- `VnpayRepository` goi `POST /api/payments/vnpay/create?paymentId=`.
- Backend tra `paymentUrl`, `qrContent`, `qrImageBase64`, `qrMimeType`, `expiresAt`.
- App hien QR VNPAY va nut mo trang thanh toan.
- `BookingConfirmationFragment` poll ticket moi 2 giay de cap nhat trang thai.
- Co the tao lai QR neu quay lai man xac nhan ma payment con pending.
- Co the huy payment neu chua thanh toan thanh cong.

Ve cua toi:

- Goi `GET /api/mobile/users/{id}/tickets`.
- App tach ve sap toi va lich su bang helper relation.
- Ve da thanh toan co QR `BUSBOOKING-TICKET:<ticketId>` de staff quet.

Ho so:

- Goi `GET /api/mobile/users/{id}`.
- Goi `PUT /api/mobile/users/{id}` de cap nhat name/email/phone.

## 5. App Staff (`staff-app`) - Ung Dung Nhan Vien

### 5.1 Man Hinh Va Navigation

Navigation graph: `staff-app/src/main/res/navigation/staff_nav_graph.xml`.

| Man hinh | Fragment/Activity | Layout | Chuc nang |
|---|---|---|---|
| Dang nhap | `LoginFragment` | `fragment_login.xml` | Nhap server URL, email Gmail, password |
| Trang chu | `HomeFragment` | `fragment_home.xml` | Tong quan chuyen, so khach booked/check-in |
| Danh sach chuyen | `TripListFragment` | `fragment_trip_list.xml` | Chuyen sap toi va lich su |
| Chi tiet chuyen | `TripDetailFragment` | `fragment_trip_detail.xml` | Thong tin trip, hanh khach, so do ghe |
| Quet ve | `TicketScannerFragment` | `fragment_ticket_scanner.xml` | Mo camera quet QR |
| Camera QR | `StaffQrCaptureActivity` | `zxing_capture.xml` | ZXing capture activity |
| Ket qua ve | `TicketResultFragment` | `fragment_ticket_result.xml` | Xem ve hop le/khong hop le, check-in |
| Tai khoan | `ProfileFragment` | `fragment_profile.xml` | Xem session staff, dang xuat |

### 5.2 Lop Chinh Trong `staff-app`

| Nhom | File | Vai tro |
|---|---|---|
| Activity | `StaffMainActivity.kt` | Host NavController staff |
| Session | `StaffSessionManager.kt` | Luu staff dang nhap |
| API config | `StaffServerConfig.kt` | Lay base URL, cho phep nhap server tren man login |
| HTTP client | `StaffApiClient.kt` | GET/POST JSON bang `HttpURLConnection` |
| Repository | `StaffRepository.kt` | Goi API staff |
| Models | `StaffModels.kt` | DTO response/request |
| Login | `LoginViewModel.kt`, `LoginFragment.kt` | Validate Gmail, dang nhap STAFF |
| Home | `HomeViewModel.kt`, `HomeFragment.kt` | Tong quan chuyen va live refresh |
| Trip | `TripListViewModel.kt`, `TripDetailViewModel.kt`, adapters | Danh sach/chi tiet chuyen, ghe, hanh khach |
| Ticket | `TicketScannerViewModel.kt`, `TicketResultViewModel.kt` | Verify QR va check-in |
| Utils | `StaffTripFilters.kt`, `StaffFormatters.kt` | Group chuyen, format ngay/gio/trang thai |

### 5.3 Nghiep Vu App Staff

Dang nhap:

- Staff nhap server URL, email Gmail va password.
- App validate email phai co dang `...@gmail.com`.
- Goi `POST /api/staff/auth/login`.
- Backend chi chap nhan user `role='STAFF'`, `is_blocked=false`.
- Sau login, app luu `StaffUser` vao `StaffSessionManager`.

Trang chu:

- Goi `GET /api/staff/home?staffId=`.
- Hien ten staff, nha xe, so chuyen, so khach da dat, so khach da check-in.
- Chi hien toi da 2 chuyen trong ngay/chuyen gan nhat theo helper `homeTodayTrips`.

Danh sach chuyen:

- Goi `GET /api/staff/trips?staffId=`.
- `StaffTripFilters` chia thanh:
  - `upcomingTrips`: chuyen chua qua gio va chua completed/cancelled.
  - `historyTrips`: chuyen da qua gio hoac completed/cancelled.
- Staff chi thay chuyen duoc gan trong `trip_staff_assignments`.

Chi tiet chuyen:

- Goi `GET /api/staff/trips/{tripId}?staffId=`.
- Hien route, gio di, bien so, tong ghe, so ghe da dat.
- Hien danh sach hanh khach.
- Hien so do ghe:
  - `AVAILABLE`: ghe trong.
  - `BOOKED`: da co ve pending/confirmed.
  - `CHECKED_IN`: da len xe.

Quet va check-in:

```text
Staff quet QR -> POST /api/staff/tickets/verify
-> backend doc ticketId tu QR
-> kiem tra ticket ton tai, staff co quyen voi trip, status ve
-> neu hop le thi hien thong tin ve
-> POST /api/staff/tickets/{ticketId}/check-in
-> ticket CHECKED_IN + trip_seats CHECKED_IN + ticket_checkins
```

QR hop le:

- `BUSBOOKING-TICKET:<ticketId>`
- `TICKET:<ticketId>`
- Query co `ticketId=...`
- Payment reference co the map ve ticket neu backend tim duoc `paymentId`.

## 6. API Base URL

Sua `local.properties` tai root project Android:

```properties
api.baseUrl=http://10.0.2.2:8081
staff.api.baseUrl=http://10.0.2.2:8081
```

Dung emulator Android:

```properties
api.baseUrl=http://10.0.2.2:8081
staff.api.baseUrl=http://10.0.2.2:8081
```

Dung dien thoai that:

```properties
api.baseUrl=http://192.168.1.199:8081
staff.api.baseUrl=http://192.168.1.199:8081
```

Neu co nhieu URL fallback:

```properties
admin.web.baseUrls=http://10.0.2.2:8081,http://192.168.1.199:8081
staff.api.baseUrls=http://10.0.2.2:8081,http://192.168.1.199:8081
```

Ghi chu:

- `10.0.2.2` la IP dac biet de emulator truy cap localhost cua may host.
- Dien thoai that phai dung IP LAN cua may chay backend.
- Ca 2 app deu co the luu server URL da nhap de lan sau dung tiep.

## 7. Cach Chay

1. Chay MySQL/XAMPP.
2. Chay backend Spring Boot tai `http://localhost:8081`.
3. Mo Android Studio project:

```text
C:\Users\ADMIN\AndroidStudioProjects\BusBooking
```

4. Chon Run/Debug Configuration:

- `BusBooking.app` de chay app khach hang.
- `BusBooking.staff-app` de chay app nhan vien.

Lenh build:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :staff-app:assembleDebug
```

Lenh test:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :staff-app:testDebugUnitTest
```

## 8. Tai Khoan Su Dung

Tai khoan phu thuoc database backend da import.

Neu DB mau da co san:

- User app thuong dung: `0900000001` / `123`

Staff app hien tai khong dang nhap bang bien so xe. Staff dang nhap bang email Gmail cua tai khoan co `role='STAFF'`.

Cach tao staff:

1. Vao web admin backend.
2. Mo `/users`.
3. Tao staff voi email Gmail, phone, password.
4. Gan staff vao chuyen o man tao/sua trip.
5. Mo staff app va dang nhap bang email Gmail/password do.

## 9. API App User Dang Goi

| API | Noi dung |
|---|---|
| `POST /api/mobile/auth/register` | Dang ky |
| `POST /api/mobile/auth/login` | Dang nhap |
| `GET /api/mobile/users/{id}` | Ho so |
| `PUT /api/mobile/users/{id}` | Cap nhat ho so |
| `GET /api/mobile/routes/origins` | Diem di |
| `GET /api/mobile/routes/destinations` | Diem den |
| `GET /api/mobile/routes/search?q=` | Tim tuyen |
| `GET /api/mobile/trips/search` | Tim chuyen |
| `GET /api/mobile/trips/{id}` | Chi tiet chuyen |
| `GET /api/mobile/trips/{id}/availability` | Tong ghe/da dat |
| `GET /api/mobile/trips/{id}/seats` | So do ghe |
| `POST /api/mobile/tickets/book` | Dat 1 ghe |
| `POST /api/mobile/tickets/book-batch` | Dat nhieu ghe/nhieu chang |
| `GET /api/mobile/tickets/{id}` | Chi tiet ve |
| `GET /api/mobile/users/{id}/tickets` | Ve cua user |
| `POST /api/mobile/tickets/{id}/cancel` | Huy ve |
| `POST /api/payments/vnpay/create` | Tao QR/link VNPAY |
| `POST /api/payments/vnpay/cancel` | Huy payment |

## 10. API Staff App Dang Goi

| API | Noi dung |
|---|---|
| `POST /api/staff/auth/login` | Dang nhap staff |
| `GET /api/staff/home?staffId=` | Tong quan |
| `GET /api/staff/trips?staffId=` | Danh sach chuyen |
| `GET /api/staff/trips/{tripId}?staffId=` | Chi tiet chuyen |
| `POST /api/staff/tickets/verify` | Xac minh QR |
| `POST /api/staff/tickets/{ticketId}/check-in` | Check-in |

## 11. Trang Thai Va Cach Hien Thi

Ticket:

| Status | App khach hang | Staff app |
|---|---|---|
| `PENDING_PAYMENT` | Cho thanh toan, co the huy | Ve chua hop le de len xe |
| `CONFIRMED` | Ve da thanh toan, hien QR ve | Ve hop le, co the check-in |
| `CHECKED_IN` | Da len xe | Da check-in |
| `PAYMENT_FAILED` | Thanh toan that bai | Ve khong hop le |
| `CANCELLED` | Da huy | Ve khong hop le |

Seat:

| Status | Y nghia |
|---|---|
| `AVAILABLE` | Ghe trong, user/staff thay ghe trong |
| `BOOKED` | Da co ve pending/confirmed |
| `CHECKED_IN` | Hanh khach da len xe |

## 12. Cau Hoi Van Dap Nhanh

**Android co dung Room/Firebase cho du lieu nghiep vu khong?**
Khong. Du lieu nghiep vu nam tren MySQL/backend. Android goi REST API.

**Vi sao emulator dung `10.0.2.2`?**
Vi `localhost` tren emulator la chinh emulator, con `10.0.2.2` tro ve may host dang chay Spring Boot.

**App user render ghe nhu the nao?**
App lay ghe tu `/api/mobile/trips/{id}/seats`, tach theo `floor`, sap xep bang `row_index` va `column_index`, chen o trong invisible neu cot khong co ghe.

**Ve nao moi co QR de staff quet?**
Ve da thanh toan thanh cong, status `CONFIRMED` hoac da check-in `CHECKED_IN`.

**QR thanh toan va QR ve khac nhau the nao?**
QR thanh toan la URL VNPAY. QR ve la `BUSBOOKING-TICKET:<ticketId>` de staff check-in.

**Dat khu hoi duoc xu ly ra sao?**
App luu ghe chieu di, tim chieu ve, sau do goi `book-batch` voi 2 segment. Backend tao nhieu ticket chung 1 payment.

**Staff co xem duoc tat ca chuyen khong?**
Khong. Backend chi tra chuyen staff duoc phan cong active.

**Staff app dang nhap bang bien so xe khong?**
Khong. Code hien tai validate email Gmail va backend tim user `role='STAFF'` theo email.

**Neu mat ket noi backend thi sao?**
`ApiClient`/`StaffApiClient` thu lan luot cac base URL fallback. Neu van loi se tra message ket noi/API.

**Vi sao can nhap server URL tren staff app?**
De linh hoat doi backend giua emulator, dien thoai that, IP LAN hoac tunnel public.

## 13. Test Hien Co

```text
app/src/test/java/com/example/busbooking/
  PasswordHasherTest.kt
  ExampleUnitTest.kt

staff-app/src/test/java/com/example/busbooking/staff/utils/
  StaffFormattersTest.kt
```

Chay:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :staff-app:testDebugUnitTest
```
