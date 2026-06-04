# Flow phát triển Web User BusBooking

File này dùng để thống nhất luồng làm web USER trong project Spring Boot:

`C:\Users\ADMIN\IdeaProjects\BusBooking`

Web admin hiện tại giữ nguyên actor ADMIN. Web user sẽ được thêm vào cùng backend nhưng tách route, giao diện và session để tránh xung đột.

## 0. Quy tắc bắt buộc trước khi build Web User

- Không làm ảnh hưởng web admin hiện có.
- Không đổi route admin hiện có:
  - `/login`
  - `/dashboard`
  - `/routes`
  - `/buses`
  - `/trips`
  - `/users`
  - `/tickets`
  - `/payments`
- Không sửa giao diện admin nếu không được yêu cầu trực tiếp.
- Không dùng chung template admin cho USER.
- Không dùng chung file CSS admin cho USER.
- Web USER chỉ được thêm mới dưới namespace `/user/**`.
- Static riêng của USER đặt dưới `/css/user.css`, có thể thêm `/js/user.js` nếu cần.
- Web USER được phép dùng lại backend/database/service/API hiện có:
  - `users`
  - `routes`
  - `buses`
  - `trips`
  - `seats`
  - `tickets`
  - `payments`
  - `VnpayPaymentService`
  - `QrCodeService`
- Nếu cần bổ sung method service dùng chung thì phải giữ nguyên hành vi admin hiện tại.
- Nếu cần chỉnh `SecurityConfig`, chỉ thêm permit hoặc rule cho `/user/**`, không thay đổi login/admin behavior.
- Nếu cần chỉnh database thì phải đảm bảo admin, app user và staff app vẫn đọc được dữ liệu cũ.

## 0.1. Nguồn tham chiếu giao diện từ app user Android

Khi build Web USER, đọc và bám theo các file app user sau:

| Màn hình web user | File Android tham chiếu |
|---|---|
| Trang chủ | `C:\Users\ADMIN\AndroidStudioProjects\BusBooking\app\src\main\res\layout\fragment_home.xml` |
| Logic trang chủ | `C:\Users\ADMIN\AndroidStudioProjects\BusBooking\app\src\main\java\com\example\busbooking\presentation\ui\HomeFragment.kt` |
| Kết quả tìm chuyến | `C:\Users\ADMIN\AndroidStudioProjects\BusBooking\app\src\main\res\layout\fragment_trip_list.xml` |
| Card chuyến | `C:\Users\ADMIN\AndroidStudioProjects\BusBooking\app\src\main\res\layout\item_trip.xml` |
| Chi tiết chuyến | `C:\Users\ADMIN\AndroidStudioProjects\BusBooking\app\src\main\res\layout\fragment_trip_details.xml` |
| Chọn ghế | `C:\Users\ADMIN\AndroidStudioProjects\BusBooking\app\src\main\res\layout\fragment_seat_selection.xml` |
| Item ghế | `C:\Users\ADMIN\AndroidStudioProjects\BusBooking\app\src\main\res\layout\item_seat.xml` |
| Logic render ghế | `C:\Users\ADMIN\AndroidStudioProjects\BusBooking\app\src\main\java\com\example\busbooking\presentation\adapter\SeatAdapter.kt` |
| Vé của tôi | `C:\Users\ADMIN\AndroidStudioProjects\BusBooking\app\src\main\res\layout\fragment_my_tickets.xml` |
| Chi tiết vé | `C:\Users\ADMIN\AndroidStudioProjects\BusBooking\app\src\main\res\layout\fragment_ticket_details.xml` |
| Hồ sơ | `C:\Users\ADMIN\AndroidStudioProjects\BusBooking\app\src\main\res\layout\fragment_user_profile.xml` |
| Tuyến xe | `C:\Users\ADMIN\AndroidStudioProjects\BusBooking\app\src\main\res\layout\item_route_catalog.xml` |

## 0.2. Quy tắc giao diện ghế 34 và 24

- Web USER không tự hardcode 40 ghế.
- Web USER không tự sinh ghế ở frontend.
- Web USER render ghế từ dữ liệu backend trả về:
  - `seat_number`
  - `floor`
  - `row_index`
  - `column_index`
  - trạng thái đã đặt/chưa đặt
- Layout 34 ghế hiện có trong app user:
  - 2 tầng.
  - Mỗi tầng 17 ghế.
  - Grid 3 cột.
  - Có ô trống để tạo lối đi như app user.
- Layout 24 ghế:
  - Chuẩn bị code render động theo `total_seats = 24`.
  - Không tự đoán sơ đồ 24 nếu chưa có mẫu.
  - Khi bạn gửi giao diện 24 ghế, cập nhật rule render riêng cho `total_seats = 24`.
- Nếu xe có `total_seats = 34`, dùng layout 34.
- Nếu xe có `total_seats = 24`, dùng layout 24 sau khi có mẫu.
- Nếu total khác 24/34 thì vẫn render theo `row_index` và `column_index` từ DB, nhưng không tạo layout mới.

## 1. Nguyên tắc chung

- Web admin dùng cho ADMIN, giữ các route hiện tại như `/login`, `/dashboard`, `/trips`, `/users`, `/tickets`, `/payments`.
- Web user dùng cho USER, tạo route riêng dưới `/user/**`.
- USER đăng nhập bằng số điện thoại và mật khẩu.
- STAFF không dùng web user.
- STAFF chỉ dùng staff app Android.
- Không dùng lại giao diện login admin cho USER.
- Không hardcode số ghế `40` ở app user, app staff, web admin hoặc web user.
- Hiện tại chỉ hỗ trợ layout `34 ghế`.
- Layout `24 ghế` sẽ làm sau khi có giao diện mẫu.
- Số ghế hiển thị luôn lấy từ database `buses.total_seats`.
- Danh sách ghế luôn lấy từ bảng `seats`, không tự sinh ở frontend.

## 2. Route chính của Web User

| Route | Mục đích | Cần đăng nhập |
|---|---|---|
| `/user/login` | Đăng nhập USER | Không |
| `/user/register` | Đăng ký USER | Không |
| `/user/logout` | Đăng xuất USER | Có |
| `/user/home` | Trang chủ web user | Không bắt buộc |
| `/user/routes` | Danh sách tuyến xe | Không bắt buộc |
| `/user/trips/search` | Kết quả tìm chuyến | Không bắt buộc |
| `/user/trips/{id}` | Chi tiết chuyến | Không bắt buộc |
| `/user/trips/{id}/seats` | Chọn ghế | Có |
| `/user/checkout` | Tạo vé và payment | Có |
| `/user/tickets` | Vé của tôi | Có |
| `/user/tickets/{id}` | Chi tiết vé | Có |
| `/user/profile` | Hồ sơ cá nhân | Có |

## 3. Session USER

Khi đăng nhập thành công:

- Kiểm tra tài khoản tồn tại trong bảng `users`.
- Kiểm tra `role = 'USER'`.
- Kiểm tra `is_blocked = false`.
- Kiểm tra mật khẩu bằng `PasswordEncoder`.
- Lưu session:
  - `USER_ID`
  - `USER_NAME`
  - `USER_PHONE`
  - `USER_ROLE`

Khi vào các route yêu cầu đăng nhập:

- Nếu chưa có `USER_ID` trong session thì chuyển về `/user/login`.
- Nếu session không phải USER thì xóa session USER và chuyển về `/user/login`.

Không dùng Spring Security admin session để nhận diện USER web. USER web dùng `HttpSession` riêng trong controller user.

## 4. Giao diện Web User

Tạo layout riêng:

- `templates/user/fragments/layout.html`
- `templates/user/fragments/head.html`
- `static/css/user.css`

Navigation chính:

- Trang chủ
- Vé của tôi
- Tuyến xe
- Cá nhân

Phong cách lấy theo app user:

- Màu chủ đạo xanh dương.
- Nền sáng.
- Card tìm vé ở trang chủ.
- Card chuyến xe giống màn kết quả tìm chuyến Android.
- Giao diện chọn ghế 2 tầng giống app user, hiện tại dùng layout 34 ghế.

## 5. Luồng đăng ký USER

1. USER mở `/user/register`.
2. Nhập họ tên, số điện thoại, email, mật khẩu.
3. Backend kiểm tra:
   - Số điện thoại không trống.
   - Mật khẩu không trống.
   - Số điện thoại/email chưa tồn tại.
4. Insert vào bảng `users`:
   - `role = 'USER'`
   - `is_blocked = false`
   - mật khẩu được mã hóa BCrypt.
5. Chuyển về `/user/login`.

## 6. Luồng đăng nhập USER

1. USER mở `/user/login`.
2. Nhập số điện thoại và mật khẩu.
3. Backend tìm user theo `phone`.
4. Nếu không tồn tại, sai mật khẩu, bị khóa hoặc không phải USER thì báo lỗi.
5. Nếu hợp lệ thì lưu session USER.
6. Chuyển về `/user/home`.

## 7. Luồng trang chủ

Trang `/user/home` hiển thị:

- Form tìm chuyến:
  - Điểm đi
  - Điểm đến
  - Ngày đi
  - Loại hành trình: một chiều, khứ hồi
- Chuyến sắp tới của user nếu đã đăng nhập.
- Tuyến phổ biến.
- Link sang danh sách tuyến.

Khi bấm tìm vé:

- Điều hướng sang `/user/trips/search?origin=...&destination=...&tripDate=...`.

## 8. Luồng tìm chuyến

Backend truy vấn:

- Bảng `trips`.
- Join `routes`.
- Join `buses`.
- Chỉ lấy chuyến:
  - `trips.status = 'SCHEDULED'`
  - `departure_time > now`
  - đúng ngày đi
  - đúng điểm đi/điểm đến
  - xe đang hoạt động
  - còn ghế

Hiển thị mỗi chuyến:

- Tên xe.
- Biển số.
- Tuyến đường.
- Giờ đi.
- Giá vé.
- Tổng ghế.
- Số ghế còn lại.
- Trạng thái tiếng Việt.

Không hiển thị `40 giường` nếu database không có xe 40 ghế.

## 9. Luồng chi tiết chuyến

Trang `/user/trips/{id}` hiển thị:

- Tuyến đường.
- Ngày đi.
- Giờ khởi hành.
- Giờ đến.
- Xe.
- Biển số.
- Giá vé.
- Tổng ghế.
- Số ghế còn.
- Nút chọn ghế.

Nếu chuyến không còn mở bán:

- Ẩn hoặc disable nút chọn ghế.

## 10. Luồng chọn ghế 34 ghế

Trang `/user/trips/{id}/seats`:

- Bắt buộc USER đã đăng nhập.
- Load danh sách ghế từ API/service theo `trip_id`.
- Chia theo `floor = 1` và `floor = 2`.
- Mỗi tầng hiện 17 ghế.
- Ghế đã có ticket trạng thái sau thì tô đen/không chọn:
  - `PENDING_PAYMENT`
  - `PENDING`
  - `CONFIRMED`
  - `CHECKED_IN`
- Ghế trống cho phép chọn.
- USER có thể chọn một hoặc nhiều ghế.
- Tổng tiền = số ghế chọn x giá chuyến.

Chưa làm layout 24 ghế cho tới khi có mẫu.

## 11. Luồng tạo vé và payment

Khi USER xác nhận ghế:

1. Backend kiểm tra session USER.
2. Backend kiểm tra chuyến còn bán.
3. Backend kiểm tra ghế còn trống.
4. Insert ticket:
   - `status = 'PENDING_PAYMENT'`
   - `payment_id = PAY-...`
   - `refund_status = 'NONE'`
5. Insert payment:
   - `status = 'CREATED'`
   - `provider = 'VNPAY'`
6. Chuyển sang trang thanh toán hoặc trang xác nhận booking.

## 12. Luồng thanh toán VNPAY

Dùng lại service hiện có:

- `VnpayPaymentService`
- `VnpayPaymentController`

Khi USER mở thanh toán:

- Backend tạo payment URL/QR VNPAY.
- USER thanh toán qua VNPAY sandbox.
- VNPAY callback về backend.
- Backend xác minh checksum.
- Nếu thành công:
  - `payments.status = 'SUCCESS'`
  - `tickets.status = 'CONFIRMED'`
- Nếu thất bại:
  - `payments.status = 'FAILED'`
  - ticket giữ trạng thái có thể hủy hoặc chuyển trạng thái lỗi tùy logic hiện tại.

## 13. Luồng vé của tôi

Trang `/user/tickets`:

- Bắt buộc đăng nhập.
- Load ticket theo `user_id`.
- Chia thành:
  - Vé sắp tới.
  - Lịch sử vé.

Vé sắp tới gồm:

- Vé đã thanh toán và chưa qua giờ đi.
- Vé đang chờ thanh toán nếu còn hiệu lực.

Lịch sử gồm:

- Vé đã qua giờ đi.
- Vé đã hủy.
- Vé thanh toán thất bại.
- Vé hết hạn.

## 14. Luồng chi tiết vé và QR vé

Trang `/user/tickets/{id}`:

- Bắt buộc đăng nhập.
- Chỉ cho xem vé thuộc user hiện tại.
- Hiển thị:
  - Mã vé.
  - Họ tên.
  - Số điện thoại.
  - Tuyến đường.
  - Giờ đi.
  - Ghế.
  - Trạng thái thanh toán.
  - Trạng thái check-in.

QR vé:

- Chỉ hiển thị nếu `ticket.status = 'CONFIRMED'` hoặc `CHECKED_IN`.
- Nội dung QR theo chuẩn hiện tại:
  - `BUSBOOKING-TICKET:{ticketId}`
- Staff app dùng QR này để kiểm vé.

## 15. Luồng hủy vé

Chỉ cho phép hủy nếu vé chưa thanh toán thành công:

- `PENDING`
- `PENDING_PAYMENT`
- `PAYMENT_FAILED`
- `FAILED`
- `EXPIRED`

Không cho hủy nếu:

- `CONFIRMED`
- `CHECKED_IN`
- `COMPLETED`

Nếu hủy thành công:

- `tickets.status = 'CANCELLED'`
- `payments.status = 'CANCELLED'` nếu payment chưa thành công.
- Ghế được giải phóng.

## 16. Hồ sơ cá nhân

Trang `/user/profile`:

- Bắt buộc đăng nhập.
- Hiển thị:
  - Họ tên.
  - Email.
  - Số điện thoại.
  - Vai trò USER.
- Cho phép cập nhật:
  - Họ tên.
  - Email.
  - Số điện thoại.

Không cho USER đổi role.

## 17. Thứ tự triển khai

1. Tạo controller session USER.
2. Tạo service/repository đọc dữ liệu user web từ MySQL.
3. Tạo layout `templates/user/fragments`.
4. Tạo `/user/login` và `/user/register`.
5. Tạo `/user/home`.
6. Tạo `/user/routes`.
7. Tạo `/user/trips/search`.
8. Tạo `/user/trips/{id}`.
9. Tạo `/user/trips/{id}/seats`.
10. Tạo checkout tạo ticket/payment.
11. Tạo `/user/tickets`.
12. Tạo `/user/tickets/{id}` và QR vé.
13. Tạo `/user/profile`.
14. Build Spring Boot.
15. Test đăng ký, đăng nhập, tìm chuyến, chọn ghế, thanh toán, xem vé.

## 18. Những việc không làm ở giai đoạn này

- Không sửa flow admin nếu không cần.
- Không tạo actor mới.
- Không dùng lại login admin cho USER.
- Không thêm lại CARRIER.
- Không thêm layout 40 ghế.
- Không làm layout 24 ghế cho tới khi có mẫu.
- Không chuyển staff app sang web.
