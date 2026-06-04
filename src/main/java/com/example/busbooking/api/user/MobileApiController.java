package com.example.busbooking.api.user;

import com.example.busbooking.shared.service.QrCodeService;
import com.example.busbooking.shared.service.RouteCatalogService;
import com.example.busbooking.shared.service.RouteCatalogService.ProvinceLocation;
import com.example.busbooking.user.model.UserWebModels.CheckoutResult;
import com.example.busbooking.user.model.UserWebModels.RouteCard;
import com.example.busbooking.user.model.UserWebModels.SeatView;
import com.example.busbooking.user.model.UserWebModels.TicketView;
import com.example.busbooking.user.model.UserWebModels.TripView;
import com.example.busbooking.user.service.UserAuthService;
import com.example.busbooking.user.service.UserAuthService.AuthUser;
import com.example.busbooking.user.service.UserBookingService;
import com.example.busbooking.user.service.UserBookingService.BookingSegment;
import com.example.busbooking.user.service.UserWebService;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile")
public class MobileApiController {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Bangkok");
    private static final long DAY_MS = 86_400_000L;

    private final JdbcTemplate jdbcTemplate;
    private final UserAuthService userAuthService;
    private final UserBookingService userBookingService;
    private final UserWebService userWebService;
    private final QrCodeService qrCodeService;
    private final RouteCatalogService routeCatalogService;

    public MobileApiController(JdbcTemplate jdbcTemplate, UserAuthService userAuthService, UserBookingService userBookingService, UserWebService userWebService, QrCodeService qrCodeService, RouteCatalogService routeCatalogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userAuthService = userAuthService;
        this.userBookingService = userBookingService;
        this.userWebService = userWebService;
        this.qrCodeService = qrCodeService;
        this.routeCatalogService = routeCatalogService;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            return ResponseEntity.ok(toUserResponse(userAuthService.register(request.name(), request.email(), request.phone(), request.password())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (DuplicateKeyException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email hoặc số điện thoại đã tồn tại"));
        }
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        AuthUser user = userAuthService.login(request.phone(), request.password());
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Số điện thoại hoặc mật khẩu không đúng"));
        }
        return ResponseEntity.ok(toUserResponse(user));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<?> user(@PathVariable long id) {
        AuthUser user = userAuthService.findUserById(id);
        return user == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(toUserResponse(user));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable long id, @RequestBody UpdateUserRequest request) {
        try {
            AuthUser user = userAuthService.updateUser(id, request.name(), request.email(), request.phone());
            return user == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(toUserResponse(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (DuplicateKeyException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email hoặc số điện thoại đã được sử dụng"));
        }
    }

    @GetMapping("/routes/origins")
    public List<String> origins() {
        return userWebService.origins();
    }

    @GetMapping("/routes/destinations")
    public List<String> destinations() {
        return userWebService.destinations();
    }

    @GetMapping("/routes/search")
    public List<RouteResponse> searchRoutes(@RequestParam String q) {
        return userWebService.routeCards()
                .stream()
                .filter(route -> routeCatalogService.locationMatches(route.origin(), q)
                        || routeCatalogService.locationMatches(route.destination(), q))
                .map(this::toRouteResponse)
                .toList();
    }

    @GetMapping("/trips/search")
    public List<TripResponse> searchTrips(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam long tripDate,
            @RequestParam(required = false) Integer totalSeats
    ) {
        LocalDate date = LocalDate.ofInstant(Instant.ofEpochMilli(tripDate), VN_ZONE);
        return userWebService.searchTrips(origin, destination, date, totalSeats)
                .stream()
                .map(this::toTripResponse)
                .toList();
    }

    @GetMapping("/trips/{id}")
    public ResponseEntity<?> trip(@PathVariable long id) {
        TripView trip = userWebService.findTrip(id);
        return trip == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(toTripResponse(trip));
    }

    @GetMapping("/trips/{id}/availability")
    public SeatAvailabilityResponse availability(@PathVariable long id) {
        TripView trip = userWebService.findTrip(id);
        if (trip == null) {
            return new SeatAvailabilityResponse(0, 0);
        }
        int totalSeats = trip.totalSeats() == null ? 0 : trip.totalSeats();
        int availableSeats = trip.availableSeats() == null ? 0 : trip.availableSeats();
        return new SeatAvailabilityResponse(totalSeats, Math.max(totalSeats - availableSeats, 0));
    }

    @GetMapping("/trips/{id}/seats")
    public List<SeatResponse> seats(@PathVariable long id) {
        return userWebService.seatsForTrip(id).stream().map(this::toSeatResponse).toList();
    }

    @PostMapping("/tickets/book")
    public ResponseEntity<?> book(@RequestBody BookRequest request) {
        try {
            CheckoutResult checkout = userBookingService.bookSeats(request.userId(), request.tripId(), List.of(request.seatId()));
            Long ticketId = checkout.ticketIds().isEmpty() ? 0L : checkout.ticketIds().get(0);
            return ResponseEntity.ok(new BookResponse(ticketId, checkout.paymentId()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", rootMessage(e)));
        }
    }


    @PostMapping("/tickets/book-batch")
    public ResponseEntity<?> bookBatch(@RequestBody BookBatchRequest request) {
        try {
            CheckoutResult checkout = userBookingService.bookSegments(request.userId(), toBookingSegments(request.segments()));
            return ResponseEntity.ok(new BookBatchResponse(checkout.ticketIds(), checkout.paymentId()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", rootMessage(e)));
        }
    }
    @GetMapping("/tickets/{id}")
    public ResponseEntity<?> ticket(@PathVariable long id) {
        TicketView ticket = userWebService.ticket(id);
        return ticket == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(toTicketDetailsResponse(ticket));
    }

    @GetMapping("/users/{id}/tickets")
    public List<TicketDetailsResponse> userTickets(@PathVariable long id) {
        return userWebService.ticketsForUser(id).stream().map(this::toTicketDetailsResponse).toList();
    }

    @PostMapping("/tickets/{id}/cancel")
    public ResponseEntity<?> cancelTicket(@PathVariable long id, @RequestBody CancelRequest request) {
        int rows = userWebService.cancelTicket(id, request.userId(), request.reason(), request.refundAmount());
        return rows == 0
                ? ResponseEntity.badRequest().body(Map.of("message", "Chỉ có thể hủy vé chưa thanh toán hoặc thanh toán không thành công"))
                : ResponseEntity.ok(Map.of("rows", rows));
    }

    private List<BookingSegment> toBookingSegments(List<BookBatchSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        List<BookingSegment> result = new ArrayList<>();
        for (BookBatchSegment segment : segments) {
            List<Long> seatIds = segment.seats() == null
                    ? List.of()
                    : segment.seats().stream().map(BookBatchSeat::id).toList();
            result.add(new BookingSegment(segment.tripId(), seatIds, toBigDecimal(segment.price())));
        }
        return result;
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.getMessage() : current.getMessage();
    }

    private RouteResponse toRouteResponse(RouteCard route) {
        return new RouteResponse(route.id(), route.origin(), route.destination(), route.distance(), true, 0L);
    }

    private TripResponse toTripResponse(TripView trip) {
        RouteResponse route = new RouteResponse(trip.routeId(), trip.origin(), trip.destination(), trip.distance(), true, 0L);
        BusResponse bus = new BusResponse(trip.busId(), trip.busName(), trip.totalSeats(), trip.licensePlate(), "", true, 0L);
        return new TripResponse(
                trip.id(),
                trip.routeId(),
                trip.busId(),
                trip.departureTime(),
                trip.arrivalTime(),
                toDouble(trip.price()),
                trip.tripDate(),
                trip.status(),
                0L,
                route,
                bus,
                trip.availableSeats()
        );
    }

    private SeatResponse toSeatResponse(SeatView seat) {
        return new SeatResponse(
                seat.id(),
                seat.busId(),
                seat.seatNumber(),
                seat.floor(),
                seat.rowIndex(),
                seat.columnIndex(),
                seat.window(),
                seat.aisle(),
                seat.seatType(),
                0L,
                seat.booked()
        );
    }

    private TicketDetailsResponse toTicketDetailsResponse(TicketView ticket) {
        UserResponse user = new UserResponse(
                ticket.userId(),
                ticket.passengerName(),
                ticket.passengerEmail(),
                ticket.passengerPhone(),
                "USER",
                false,
                0L
        );
        TicketResponse ticketResponse = new TicketResponse(
                ticket.id(),
                ticket.userId(),
                ticket.tripId(),
                ticket.seatId(),
                ticket.bookingTime(),
                ticket.status(),
                ticket.cancellationReason(),
                ticket.refundAmount() == null ? null : ticket.refundAmount().doubleValue(),
                ticket.refundStatus(),
                ticket.paymentId(),
                ticket.qrContent(),
                ticket.qrImageBase64(),
                ticket.qrMimeType()
        );
        return new TicketDetailsResponse(ticketResponse, user, toTripResponse(ticket.trip()), toSeatResponse(ticket.seat()));
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private String baseTripSql() {
        return """
                SELECT t.*, r.origin_id, r.destination_id, r.origin, r.destination, r.distance, b.bus_name, b.total_seats, b.license_plate,
                       (b.total_seats - COALESCE(booked.booked_seats, 0)) AS available_seats
                FROM trips t JOIN routes r ON r.id=t.route_id JOIN buses b ON b.id=t.bus_id
                LEFT JOIN (SELECT trip_id, COUNT(*) booked_seats FROM tickets WHERE status IN ('PENDING_PAYMENT','PENDING','CONFIRMED','CHECKED_IN') GROUP BY trip_id) booked ON booked.trip_id=t.id
                """;
    }

    private String ticketSql() {
        return """
                SELECT tk.*, u.name, u.email, u.phone, u.role, u.is_blocked, u.created_at user_created_at,
                       t.departure_time, t.arrival_time, t.price, t.trip_date, t.status trip_status,
                       r.id route_id, r.origin_id, r.destination_id, r.origin, r.destination, r.distance,
                       b.id bus_id2, b.bus_name, b.total_seats, b.license_plate,
                       s.seat_number, s.floor, s.row_index, s.column_index, s.is_window, s.is_aisle, s.seat_type, s.created_at seat_created_at
                FROM tickets tk
                JOIN users u ON u.id=tk.user_id
                JOIN trips t ON t.id=tk.trip_id
                JOIN routes r ON r.id=t.route_id
                JOIN buses b ON b.id=t.bus_id
                JOIN seats s ON s.id=tk.seat_id
                """;
    }

    private long dayStart(long millis) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(millis), VN_ZONE)
                .atStartOfDay(VN_ZONE)
                .toInstant()
                .toEpochMilli();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean sameLocation(ProvinceLocation actual, ProvinceLocation requested) {
        if (!text(actual.id()).isBlank() && !text(requested.id()).isBlank()) {
            return text(actual.id()).equals(text(requested.id()));
        }
        return routeCatalogService.locationMatches(actual.name(), requested.name());
    }

    private UserResponse toUserResponse(AuthUser user) {
        return new UserResponse(user.id(), user.name(), user.email(), user.phone(), user.role(), user.blocked(), user.createdAt());
    }

    private RouteResponse mapRoute(ResultSet rs, int rowNum) throws SQLException {
        ProvinceLocation origin = routeCatalogService.canonicalLocation(rs.getString("origin_id"), rs.getString("origin"));
        ProvinceLocation destination = routeCatalogService.canonicalLocation(rs.getString("destination_id"), rs.getString("destination"));
        return new RouteResponse(rs.getLong("id"), origin.name(), destination.name(), rs.getInt("distance"), rs.getBoolean("is_active"), rs.getLong("created_at"));
    }

    private TripResponse mapTrip(ResultSet rs, int rowNum) throws SQLException {
        return new TripResponse(rs.getLong("id"), rs.getLong("route_id"), rs.getLong("bus_id"), rs.getLong("departure_time"), rs.getLong("arrival_time"), rs.getDouble("price"), rs.getLong("trip_date"), rs.getString("status"), rs.getLong("created_at"), mapRouteFromTrip(rs), mapBusFromTrip(rs), rs.getInt("available_seats"));
    }

    private RouteResponse mapRouteFromTrip(ResultSet rs) throws SQLException {
        ProvinceLocation origin = routeCatalogService.canonicalLocation(rs.getString("origin_id"), rs.getString("origin"));
        ProvinceLocation destination = routeCatalogService.canonicalLocation(rs.getString("destination_id"), rs.getString("destination"));
        return new RouteResponse(rs.getLong("route_id"), origin.name(), destination.name(), rs.getInt("distance"), true, 0L);
    }

    private BusResponse mapBusFromTrip(ResultSet rs) throws SQLException {
        return new BusResponse(rs.getLong("bus_id"), rs.getString("bus_name"), rs.getInt("total_seats"), rs.getString("license_plate"), "", true, 0L);
    }

    private SeatResponse mapSeat(ResultSet rs, int rowNum) throws SQLException {
        return new SeatResponse(rs.getLong("id"), rs.getLong("bus_id"), rs.getString("seat_number"), rs.getInt("floor"), rs.getInt("row_index"), rs.getInt("column_index"), rs.getBoolean("is_window"), rs.getBoolean("is_aisle"), rs.getString("seat_type"), rs.getLong("created_at"), rs.getBoolean("booked"));
    }

    private TicketDetailsResponse mapTicketDetails(ResultSet rs, int rowNum) throws SQLException {
        UserResponse user = new UserResponse(rs.getLong("user_id"), rs.getString("name"), rs.getString("email"), rs.getString("phone"), rs.getString("role"), rs.getBoolean("is_blocked"), rs.getLong("user_created_at"));
        ProvinceLocation origin = routeCatalogService.canonicalLocation(rs.getString("origin_id"), rs.getString("origin"));
        ProvinceLocation destination = routeCatalogService.canonicalLocation(rs.getString("destination_id"), rs.getString("destination"));
        RouteResponse route = new RouteResponse(rs.getLong("route_id"), origin.name(), destination.name(), rs.getInt("distance"), true, 0L);
        BusResponse bus = new BusResponse(rs.getLong("bus_id2"), rs.getString("bus_name"), rs.getInt("total_seats"), rs.getString("license_plate"), "", true, 0L);
        TripResponse trip = new TripResponse(rs.getLong("trip_id"), rs.getLong("route_id"), rs.getLong("bus_id2"), rs.getLong("departure_time"), rs.getLong("arrival_time"), rs.getDouble("price"), rs.getLong("trip_date"), rs.getString("trip_status"), 0L, route, bus, 0);
        SeatResponse seat = new SeatResponse(rs.getLong("seat_id"), rs.getLong("bus_id2"), rs.getString("seat_number"), rs.getInt("floor"), rs.getInt("row_index"), rs.getInt("column_index"), rs.getBoolean("is_window"), rs.getBoolean("is_aisle"), rs.getString("seat_type"), rs.getLong("seat_created_at"), true);
        String status = rs.getString("status");
        String qrContent = ticketQrContent(rs.getLong("id"));
        boolean hasTicketQr = isPaidTicketStatus(status);
        TicketResponse ticket = new TicketResponse(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getLong("trip_id"),
                rs.getLong("seat_id"),
                rs.getLong("booking_time"),
                status,
                rs.getString("cancellation_reason"),
                rs.getDouble("refund_amount"),
                rs.getString("refund_status"),
                rs.getString("payment_id"),
                hasTicketQr ? qrContent : "",
                hasTicketQr ? qrCodeService.createPngBase64(qrContent) : "",
                hasTicketQr ? "image/png" : ""
        );
        return new TicketDetailsResponse(ticket, user, trip, seat);
    }

    private boolean isPaidTicketStatus(String status) {
        return "CONFIRMED".equalsIgnoreCase(status) || "CHECKED_IN".equalsIgnoreCase(status);
    }

    private String ticketQrContent(Long ticketId) {
        return "BUSBOOKING-TICKET:" + ticketId;
    }

    public record RegisterRequest(String name, String email, String password, String phone) {}
    public record LoginRequest(String phone, String password) {}
    public record UpdateUserRequest(String name, String email, String phone) {}
    public record BookRequest(Long userId, Long tripId, Long seatId) {}
    public record CancelRequest(Long userId, String reason, Double refundAmount) {}
    public record BookResponse(Long ticketId, String paymentId) {}
    public record BookBatchSeat(Long id, String seatNumber) {}
    public record BookBatchSegment(Long tripId, List<BookBatchSeat> seats, Double price) {}
    public record BookBatchRequest(Long userId, List<BookBatchSegment> segments) {}
    public record BookBatchResponse(List<Long> ticketIds, String paymentId) {}
    public record SeatAvailabilityResponse(Integer totalSeats, Integer bookedSeats) {}
    public record UserResponse(Long id, String name, String email, String phone, String role, boolean isBlocked, Long createdAt) {}
    public record RouteResponse(Long id, String origin, String destination, Integer distance, boolean isActive, Long createdAt) {}
    public record BusResponse(Long id, String busName, Integer totalSeats, String licensePlate, String seatLayoutJson, boolean isActive, Long createdAt) {}
    public record TripResponse(Long id, Long routeId, Long busId, Long departureTime, Long arrivalTime, Double price, Long tripDate, String status, Long createdAt, RouteResponse route, BusResponse bus, Integer availableSeats) {}
    public record SeatResponse(Long id, Long busId, String seatNumber, Integer floor, Integer rowIndex, Integer columnIndex, boolean isWindow, boolean isAisle, String seatType, Long createdAt, boolean booked) {}
    public record TicketResponse(Long id, Long userId, Long tripId, Long seatId, Long bookingTime, String status, String cancellationReason, Double refundAmount, String refundStatus, String paymentId, String qrContent, String qrImageBase64, String qrMimeType) {}
    public record TicketDetailsResponse(TicketResponse ticket, UserResponse user, TripResponse tripWithRouteAndBus, SeatResponse seat) {}
}


