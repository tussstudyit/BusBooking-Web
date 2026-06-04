package com.example.busbooking.api.staff;

import com.example.busbooking.shared.service.RouteCatalogService;
import com.example.busbooking.shared.service.RouteCatalogService.ProvinceLocation;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/staff")
public class StaffApiController {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Bangkok");
    private static final Pattern TICKET_PARAM_PATTERN = Pattern.compile("(?i)(?:ticketId|ticket_id|ticket|id)=([0-9]+)");
    private static final Pattern TICKET_TOKEN_PATTERN = Pattern.compile("(?i)(?:TICKET|BUSBOOKING-TICKET)[:\\-# ]+([0-9]+)");
    private static final Pattern PAYMENT_PARAM_PATTERN = Pattern.compile("(?i)(?:paymentId|payment_id|vnp_TxnRef)=([^&\\s]+)");
    private static final Pattern PAYMENT_TOKEN_PATTERN = Pattern.compile("(?i)(PAY-[A-Z0-9]+)");

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final RouteCatalogService routeCatalogService;

    public StaffApiController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, RouteCatalogService routeCatalogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.routeCatalogService = routeCatalogService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        StaffAccount account = findAccountByEmail(text(request.login()));
        if (account == null || account.isBlocked()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Không tìm thấy tài khoản theo email hoặc tài khoản đã bị khóa"));
        }
        if (!canUseStaffApp(account.user().role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Chỉ tài khoản nhân viên mới được dùng ứng dụng này"));
        }
        if (!passwordEncoder.matches(text(request.password()), account.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Email hoặc mật khẩu không đúng"));
        }
        return ResponseEntity.ok(new LoginResponse(account.user(), ""));
    }

    @GetMapping("/home")
    public StaffHomeSummary home(@RequestParam long staffId) {
        StaffUser user = requireStaffUser(staffId);
        StaffWindow window = staffTripWindow(user);
        List<StaffTripSummary> todayTrips = findStaffTrips(user, window.start(), window.end());
        List<Object> checkInParams = scopedParams(user, window.start(), window.end());
        Integer checkedIn = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM tickets tk JOIN trips t ON t.id=tk.trip_id
                WHERE tk.status='CHECKED_IN'
                  AND t.departure_time>=? AND t.departure_time<?
                  AND t.status NOT IN ('CANCELLED','COMPLETED')
                  AND %s
                """.formatted(tripScopeSql(user)), Integer.class, checkInParams.toArray());
        List<Object> bookedParams = scopedParams(user, window.start(), window.end());
        Integer booked = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM tickets tk JOIN trips t ON t.id=tk.trip_id
                WHERE tk.status IN ('PENDING_PAYMENT','PENDING','CONFIRMED','CHECKED_IN')
                  AND t.departure_time>=? AND t.departure_time<?
                  AND t.status NOT IN ('CANCELLED','COMPLETED')
                  AND %s
                """.formatted(tripScopeSql(user)), Integer.class, bookedParams.toArray());
        return new StaffHomeSummary(
                user.name(),
                user.companyName(),
                todayTrips.size(),
                checkedIn == null ? 0 : checkedIn,
                booked == null ? 0 : booked,
                todayTrips
        );
    }

    @GetMapping("/trips")
    public List<StaffTripSummary> trips(@RequestParam long staffId) {
        StaffUser staff = requireStaffUser(staffId);
        return findAllStaffTrips(staff);
    }

    private List<StaffTripSummary> findStaffTrips(StaffUser staff, long start, long end) {
        List<Object> params = scopedParams(staff, start, end);
        return jdbcTemplate.query(baseTripSql() + """
                WHERE t.departure_time>=?
                  AND t.departure_time<?
                  AND t.status NOT IN ('CANCELLED','COMPLETED')
                  AND %s
                ORDER BY t.departure_time ASC
                LIMIT 200
                """.formatted(tripScopeSql(staff)), this::mapTripSummary, params.toArray());
    }

    private List<StaffTripSummary> findAllStaffTrips(StaffUser staff) {
        List<Object> params = scopedParams(staff);
        return jdbcTemplate.query(baseTripSql() + """
                WHERE %s
                ORDER BY t.departure_time ASC
                LIMIT 200
                """.formatted(tripScopeSql(staff)), this::mapTripSummary, params.toArray());
    }

    @GetMapping("/trips/{tripId}")
    public StaffTripDetail trip(@PathVariable long tripId, @RequestParam long staffId) {
        StaffUser staff = requireStaffUser(staffId);
        List<Object> params = scopedParams(staff, tripId);
        List<StaffTripSummary> trips = jdbcTemplate.query(baseTripSql() + " WHERE t.id=? AND " + tripScopeSql(staff), this::mapTripSummary, params.toArray());
        if (trips.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chuyến xe");
        }
        StaffTripSummary trip = trips.get(0);
        List<StaffPassenger> passengers = jdbcTemplate.query("""
                SELECT tk.id AS ticket_id, tk.status AS ticket_status, u.name, u.phone, s.seat_number
                FROM tickets tk
                JOIN users u ON u.id=tk.user_id
                JOIN seats s ON s.id=tk.seat_id
                WHERE tk.trip_id=? AND tk.status NOT IN ('CANCELLED','PAYMENT_FAILED')
                ORDER BY s.floor, s.row_index, s.column_index, s.id
                """, this::mapPassenger, tripId);
        List<StaffSeat> seats = jdbcTemplate.query("""
                SELECT s.id AS seat_id, s.seat_number, s.floor, s.row_index, s.column_index,
                       tk.id AS ticket_id, tk.status AS ticket_status, u.name, u.phone
                FROM trips t
                JOIN seats s ON s.bus_id=t.bus_id
                LEFT JOIN tickets tk ON tk.trip_id=t.id
                    AND tk.status NOT IN ('CANCELLED','PAYMENT_FAILED')
                    AND (
                        tk.seat_id=s.id
                        OR EXISTS (
                            SELECT 1
                            FROM seats booked_seat
                            WHERE booked_seat.id=tk.seat_id
                              AND booked_seat.seat_number=s.seat_number
                        )
                    )
                LEFT JOIN users u ON u.id=tk.user_id
                WHERE t.id=?
                ORDER BY s.floor, s.row_index, s.column_index, s.id
                """, this::mapSeat, tripId);
        return new StaffTripDetail(
                trip.id(),
                trip.code(),
                trip.origin(),
                trip.destination(),
                trip.departureTime(),
                trip.licensePlate(),
                trip.totalSeats(),
                trip.bookedSeats(),
                trip.status(),
                "",
                staff.name(),
                passengers,
                seats
        );
    }

    @PostMapping("/trips/{tripId}/start")
    public ApiMessage startTrip(@PathVariable long tripId, @RequestBody StaffActionRequest request) {
        if (request.staffId() != null) {
            requireStaffUser(request.staffId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nhan vien khong co quyen bat dau chuyen");
        }
        StaffUser staff = requireStaffUser(request.staffId());
        requireTripAccess(tripId, staff);
        int rows = jdbcTemplate.update(
                "UPDATE trips SET status='RUNNING', updated_at=? WHERE id=?",
                System.currentTimeMillis(),
                tripId
        );
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chuyến xe");
        }
        return new ApiMessage("Đã bắt đầu chuyến");
    }

    @PostMapping("/trips/{tripId}/complete")
    public ApiMessage completeTrip(@PathVariable long tripId, @RequestBody StaffActionRequest request) {
        if (request.staffId() != null) {
            requireStaffUser(request.staffId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nhan vien khong co quyen ket thuc chuyen");
        }
        StaffUser staff = requireStaffUser(request.staffId());
        requireTripAccess(tripId, staff);
        int rows = jdbcTemplate.update(
                "UPDATE trips SET status='COMPLETED', updated_at=? WHERE id=?",
                System.currentTimeMillis(),
                tripId
        );
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chuyến xe");
        }
        return new ApiMessage("Đã hoàn thành chuyến");
    }

    @PostMapping("/tickets/verify")
    public TicketVerificationResult verifyTicket(@RequestBody TicketVerifyRequest request) {
        StaffUser staff = requireStaffUser(request.staffId());
        Long ticketId = resolveTicketId(text(request.qrContent()));
        if (ticketId == null) {
            return TicketVerificationResult.invalid("Mã QR vé không hợp lệ");
        }
        Long tripId = findTicketTripId(ticketId);
        if (tripId == null) {
            return TicketVerificationResult.invalid("Không tìm thấy vé");
        }
        requireTripAccess(tripId, staff);
        TicketVerificationResult result = findTicketResult(ticketId);
        return result == null ? TicketVerificationResult.invalid("Không tìm thấy vé") : result;
    }

    @PostMapping("/tickets/{ticketId}/check-in")
    public ResponseEntity<?> checkInTicket(@PathVariable long ticketId, @RequestBody StaffActionRequest request) {
        StaffUser staff = requireStaffUser(request.staffId());
        TicketVerificationResult current = findTicketResult(ticketId);
        if (current == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Không tìm thấy vé"));
        }
        Long tripId = findTicketTripId(ticketId);
        if (tripId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Không tìm thấy vé"));
        }
        requireTripAccess(tripId, staff);
        if (!current.valid()) {
            return ResponseEntity.badRequest().body(Map.of("message", current.message().isBlank() ? "Vé không hợp lệ" : current.message()));
        }
        if ("CHECKED_IN".equals(current.checkInStatus())) {
            return ResponseEntity.ok(current);
        }
        long now = System.currentTimeMillis();
        jdbcTemplate.update("UPDATE tickets SET status='CHECKED_IN', updated_at=? WHERE id=?", now, ticketId);
        jdbcTemplate.update("""
                INSERT INTO trip_seats(trip_id,seat_id,ticket_id,user_id,status,created_at,updated_at)
                SELECT trip_id,seat_id,id,user_id,'CHECKED_IN',?,? FROM tickets WHERE id=?
                ON DUPLICATE KEY UPDATE ticket_id=VALUES(ticket_id),user_id=VALUES(user_id),status='CHECKED_IN',updated_at=VALUES(updated_at)
                """, now, now, ticketId);
        jdbcTemplate.update("""
                INSERT INTO ticket_checkins(ticket_id,trip_id,staff_id,method,checked_in_at)
                SELECT id,trip_id,?,'QR',? FROM tickets WHERE id=?
                ON DUPLICATE KEY UPDATE staff_id=VALUES(staff_id),method=VALUES(method),checked_in_at=VALUES(checked_in_at)
                """, staff.id(), now, ticketId);
        return ResponseEntity.ok(findTicketResult(ticketId));
    }

    private String baseTripSql() {
        return """
                SELECT t.id, t.departure_time, t.status, r.origin_id, r.destination_id, r.origin, r.destination, b.license_plate, b.total_seats,
                       COALESCE(booked.booked_seats, 0) AS booked_seats
                FROM trips t
                JOIN routes r ON r.id=t.route_id
                JOIN buses b ON b.id=t.bus_id
                LEFT JOIN (
                    SELECT trip_id, COUNT(*) booked_seats
                    FROM tickets
                    WHERE status IN ('PENDING_PAYMENT','PENDING','CONFIRMED','CHECKED_IN')
                    GROUP BY trip_id
                ) booked ON booked.trip_id=t.id
                """;
    }

    private String tripScopeSql(StaffUser staff) {
        return """
                EXISTS (
                    SELECT 1
                    FROM trip_staff_assignments tsa
                    WHERE tsa.staff_id=? AND tsa.trip_id=t.id AND tsa.status='ACTIVE'
                )
                """;
    }

    private List<Object> scopedParams(StaffUser staff, Object... leadingParams) {
        List<Object> params = new ArrayList<>();
        for (Object param : leadingParams) {
            params.add(param);
        }
        params.add(staff.id());
        return params;
    }

    private void requireTripAccess(long tripId, StaffUser staff) {
        List<Object> params = scopedParams(staff, tripId);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trips t WHERE t.id=? AND " + tripScopeSql(staff),
                Integer.class,
                params.toArray()
        );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chuyến xe");
        }
    }

    private StaffTripSummary mapTripSummary(ResultSet rs, int rowNum) throws SQLException {
        long id = rs.getLong("id");
        ProvinceLocation origin = routeCatalogService.canonicalLocation(rs.getString("origin_id"), rs.getString("origin"));
        ProvinceLocation destination = routeCatalogService.canonicalLocation(rs.getString("destination_id"), rs.getString("destination"));
        return new StaffTripSummary(
                id,
                "TRIP-" + id,
                origin.name(),
                destination.name(),
                rs.getLong("departure_time"),
                rs.getString("license_plate"),
                rs.getInt("total_seats"),
                rs.getInt("booked_seats"),
                rs.getString("status")
        );
    }

    private StaffPassenger mapPassenger(ResultSet rs, int rowNum) throws SQLException {
        String ticketStatus = rs.getString("ticket_status");
        return new StaffPassenger(
                rs.getLong("ticket_id"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("seat_number"),
                paymentStatus(ticketStatus),
                checkInStatus(ticketStatus)
        );
    }

    private StaffSeat mapSeat(ResultSet rs, int rowNum) throws SQLException {
        long ticketId = rs.getLong("ticket_id");
        if (rs.wasNull()) {
            ticketId = 0L;
        }
        String ticketStatus = text(rs.getString("ticket_status"));
        String status = switch (ticketStatus.toUpperCase(Locale.ROOT)) {
            case "CHECKED_IN" -> "CHECKED_IN";
            case "PENDING", "PENDING_PAYMENT", "CONFIRMED" -> "BOOKED";
            default -> "AVAILABLE";
        };
        StaffPassenger passenger = ticketId <= 0L ? null : new StaffPassenger(
                ticketId,
                text(rs.getString("name")),
                text(rs.getString("phone")),
                rs.getString("seat_number"),
                paymentStatus(ticketStatus),
                checkInStatus(ticketStatus)
        );
        return new StaffSeat(
                rs.getLong("seat_id"),
                rs.getString("seat_number"),
                rs.getInt("floor"),
                rs.getInt("row_index"),
                rs.getInt("column_index"),
                status,
                passenger
        );
    }

    private TicketVerificationResult findTicketResult(long ticketId) {
        List<TicketVerificationResult> results = jdbcTemplate.query("""
                SELECT tk.id AS ticket_id, tk.trip_id, tk.status AS ticket_status, u.name, u.phone,
                       t.departure_time, r.origin_id, r.destination_id, r.origin, r.destination, s.seat_number
                FROM tickets tk
                JOIN users u ON u.id=tk.user_id
                JOIN trips t ON t.id=tk.trip_id
                JOIN routes r ON r.id=t.route_id
                JOIN seats s ON s.id=tk.seat_id
                WHERE tk.id=?
                """, (rs, rowNum) -> {
            String status = rs.getString("ticket_status");
            boolean valid = "CONFIRMED".equals(status) || "CHECKED_IN".equals(status);
            String message = valid ? "" : invalidTicketMessage(status);
            ProvinceLocation origin = routeCatalogService.canonicalLocation(rs.getString("origin_id"), rs.getString("origin"));
            ProvinceLocation destination = routeCatalogService.canonicalLocation(rs.getString("destination_id"), rs.getString("destination"));
            return new TicketVerificationResult(
                    valid,
                    message,
                    rs.getLong("ticket_id"),
                    rs.getLong("trip_id"),
                    rs.getString("name"),
                    rs.getString("phone"),
                    origin.name(),
                    destination.name(),
                    rs.getLong("departure_time"),
                    rs.getString("seat_number"),
                    paymentStatus(status),
                    checkInStatus(status)
            );
        }, ticketId);
        return results.isEmpty() ? null : results.get(0);
    }

    private StaffUser requireStaffUser(Long staffId) {
        StaffAccount account = staffId == null ? null : findAccountById(staffId);
        if (account == null || account.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập nhân viên đã hết hạn");
        }
        if (!canUseStaffApp(account.user().role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ tài khoản nhân viên mới được dùng ứng dụng này");
        }
        return account.user();
    }

    private StaffAccount findAccountByEmail(String login) {
        String email = login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
        if (!email.endsWith("@gmail.com")) {
            return null;
        }
        List<StaffAccount> users = jdbcTemplate.query(
                "SELECT * FROM users WHERE role='STAFF' AND LOWER(email)=? LIMIT 1",
                this::mapAccount,
                email
        );
        return users.isEmpty() ? null : users.get(0);
    }

    private StaffAccount findAccountById(long id) {
        if (id <= 0L) {
            return null;
        }
        List<StaffAccount> users = jdbcTemplate.query("SELECT * FROM users WHERE id=?", this::mapAccount, id);
        return users.isEmpty() ? null : users.get(0);
    }

    private StaffAccount mapAccount(ResultSet rs, int rowNum) throws SQLException {
        StaffUser user = new StaffUser(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("role"),
                companyName(rs.getLong("id"))
        );
        return new StaffAccount(user, rs.getString("password"), rs.getBoolean("is_blocked"));
    }

    private Long resolveTicketId(String qrContent) {
        if (qrContent.isBlank()) {
            return null;
        }
        if (qrContent.matches("\\d+")) {
            return parseLong(qrContent);
        }
        Long ticketId = findLong(TICKET_PARAM_PATTERN, qrContent);
        if (ticketId != null) {
            return ticketId;
        }
        ticketId = findLong(TICKET_TOKEN_PATTERN, qrContent);
        if (ticketId != null) {
            return ticketId;
        }
        String paymentId = findPaymentId(qrContent);
        return paymentId == null ? null : findTicketIdByPaymentId(paymentId);
    }

    private Long findTicketIdByPaymentId(String paymentId) {
        List<Long> tickets = jdbcTemplate.query("SELECT id FROM tickets WHERE payment_id=? LIMIT 1", (rs, rowNum) -> rs.getLong(1), paymentId);
        if (!tickets.isEmpty()) {
            return tickets.get(0);
        }
        tickets = jdbcTemplate.query(
                "SELECT ticket_id FROM payments WHERE (id=? OR vnp_txn_ref=?) AND ticket_id IS NOT NULL LIMIT 1",
                (rs, rowNum) -> rs.getLong(1),
                paymentId,
                paymentId
        );
        return tickets.isEmpty() ? null : tickets.get(0);
    }

    private Long findTicketTripId(long ticketId) {
        List<Long> trips = jdbcTemplate.query("SELECT trip_id FROM tickets WHERE id=? LIMIT 1", (rs, rowNum) -> rs.getLong(1), ticketId);
        return trips.isEmpty() ? null : trips.get(0);
    }

    private Long findLong(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? parseLong(matcher.group(1)) : null;
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String findPaymentId(String qrContent) {
        Matcher param = PAYMENT_PARAM_PATTERN.matcher(qrContent);
        if (param.find()) {
            return URLDecoder.decode(param.group(1), StandardCharsets.UTF_8);
        }
        Matcher token = PAYMENT_TOKEN_PATTERN.matcher(qrContent.toUpperCase(Locale.ROOT));
        return token.find() ? token.group(1) : null;
    }

    private boolean canUseStaffApp(String role) {
        return "STAFF".equalsIgnoreCase(role);
    }

    private String companyName(long staffId) {
        List<String> names = jdbcTemplate.query("""
                SELECT bc.name
                FROM staff_profiles sp
                JOIN bus_companies bc ON bc.id=sp.company_id
                WHERE sp.user_id=? AND sp.status='ACTIVE'
                LIMIT 1
                """, (rs, rowNum) -> rs.getString(1), staffId);
        if (!names.isEmpty()) {
            return names.get(0);
        }
        names = jdbcTemplate.query("""
                SELECT bc.name
                FROM staff_bus_assignments sba
                JOIN buses b ON b.id=sba.bus_id
                JOIN bus_companies bc ON bc.id=b.company_id
                WHERE sba.staff_id=? AND sba.is_active=true
                LIMIT 1
                """, (rs, rowNum) -> rs.getString(1), staffId);
        return names.isEmpty() ? "BusBooking Express" : names.get(0);
    }

    private String paymentStatus(String ticketStatus) {
        return switch (text(ticketStatus).toUpperCase(Locale.ROOT)) {
            case "CONFIRMED", "CHECKED_IN" -> "PAID";
            case "PENDING", "PENDING_PAYMENT" -> "PENDING_PAYMENT";
            default -> text(ticketStatus);
        };
    }

    private String checkInStatus(String ticketStatus) {
        return "CHECKED_IN".equalsIgnoreCase(ticketStatus) ? "CHECKED_IN" : "NOT_CHECKED_IN";
    }

    private String invalidTicketMessage(String ticketStatus) {
        return switch (text(ticketStatus).toUpperCase(Locale.ROOT)) {
            case "PENDING", "PENDING_PAYMENT" -> "Vé chưa thanh toán";
            case "CANCELLED" -> "Vé đã bị hủy";
            case "PAYMENT_FAILED" -> "Thanh toán vé thất bại";
            default -> "Vé không hợp lệ";
        };
    }

    private long dayStart(long millis) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(millis), VN_ZONE)
                .atStartOfDay(VN_ZONE)
                .toInstant()
                .toEpochMilli();
    }

    private StaffWindow staffTripWindow(StaffUser staff) {
        long now = System.currentTimeMillis();
        long todayStart = dayStart(now);
        long todayEnd = todayStart + 86_400_000L;
        if (countStaffTrips(staff, todayStart, todayEnd) > 0) {
            return new StaffWindow(todayStart, todayEnd);
        }
        return new StaffWindow(todayEnd, todayEnd + 86_400_000L);
    }

    private int countStaffTrips(StaffUser staff, long start, long end) {
        List<Object> params = scopedParams(staff, start, end);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM trips t
                WHERE t.departure_time>=?
                  AND t.departure_time<?
                  AND t.status NOT IN ('CANCELLED','COMPLETED')
                  AND %s
                """.formatted(tripScopeSql(staff)), Integer.class, params.toArray());
        return count == null ? 0 : count;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizePlate(String value) {
        return text(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public record LoginRequest(String login, String password) {}
    public record LoginResponse(StaffUser user, String token) {}
    public record StaffUser(Long id, String name, String email, String phone, String role, String companyName) {}
    private record StaffAccount(StaffUser user, String password, boolean isBlocked) {}
    public record StaffHomeSummary(String staffName, String companyName, Integer assignedTripsToday, Integer checkedInPassengers, Integer bookedPassengersToday, List<StaffTripSummary> todayTrips) {}
    public record StaffTripSummary(Long id, String code, String origin, String destination, Long departureTime, String licensePlate, Integer totalSeats, Integer bookedSeats, String status) {}
    public record StaffTripDetail(Long id, String code, String origin, String destination, Long departureTime, String licensePlate, Integer totalSeats, Integer bookedSeats, String status, String driverName, String staffName, List<StaffPassenger> passengers, List<StaffSeat> seats) {}
    public record StaffPassenger(Long ticketId, String name, String phone, String seatNumber, String paymentStatus, String checkInStatus) {}
    public record StaffSeat(Long seatId, String seatNumber, Integer floor, Integer rowIndex, Integer columnIndex, String status, StaffPassenger passenger) {}
    public record TicketVerifyRequest(String qrContent, Long staffId) {}
    public record StaffActionRequest(Long staffId) {}
    public record ApiMessage(String message) {}
    private record StaffWindow(long start, long end) {}
    public record TicketVerificationResult(boolean valid, String message, Long ticketId, Long tripId, String passengerName, String phone, String origin, String destination, Long departureTime, String seatNumber, String paymentStatus, String checkInStatus) {
        static TicketVerificationResult invalid(String message) {
            return new TicketVerificationResult(false, message, 0L, 0L, "", "", "", "", 0L, "", "", "");
        }
    }
}
