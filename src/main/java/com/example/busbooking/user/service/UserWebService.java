package com.example.busbooking.user.service;

import com.example.busbooking.shared.service.QrCodeService;
import com.example.busbooking.shared.service.RouteCatalogService;
import com.example.busbooking.shared.service.RouteCatalogService.ProvinceLocation;
import com.example.busbooking.user.model.UserWebModels.CheckoutResult;
import com.example.busbooking.user.model.UserWebModels.PaymentView;
import com.example.busbooking.user.model.UserWebModels.RouteCard;
import com.example.busbooking.user.model.UserWebModels.RouteSeatOption;
import com.example.busbooking.user.model.UserWebModels.SeatView;
import com.example.busbooking.user.model.UserWebModels.TicketView;
import com.example.busbooking.user.model.UserWebModels.TripView;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserWebService {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Bangkok");
    private static final long DAY_MS = 86_400_000L;

    private final JdbcTemplate jdbcTemplate;
    private final QrCodeService qrCodeService;
    private final RouteCatalogService routeCatalogService;
    private final UserBookingService userBookingService;

    public UserWebService(
            JdbcTemplate jdbcTemplate,
            QrCodeService qrCodeService,
            RouteCatalogService routeCatalogService,
            UserBookingService userBookingService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.qrCodeService = qrCodeService;
        this.routeCatalogService = routeCatalogService;
        this.userBookingService = userBookingService;
    }

    public List<String> origins() {
        return jdbcTemplate.query("SELECT origin_id, origin FROM routes WHERE is_active=true", (rs, rowNum) ->
                        routeCatalogService.canonicalLocation(rs.getString("origin_id"), rs.getString("origin")).name())
                .stream()
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> destinations() {
        return jdbcTemplate.query("SELECT destination_id, destination FROM routes WHERE is_active=true", (rs, rowNum) ->
                        routeCatalogService.canonicalLocation(rs.getString("destination_id"), rs.getString("destination")).name())
                .stream()
                .distinct()
                .sorted()
                .toList();
    }

    public List<RouteCard> routeCards() {
        List<RouteCard> cards = jdbcTemplate.query("""
                SELECT r.id, r.origin_id, r.destination_id, r.origin, r.destination, r.distance, r.seat_count,
                       r.suggested_price, MIN(t.departure_time) AS next_departure_time, COUNT(t.id) AS trip_count
                FROM routes r
                LEFT JOIN trips t ON t.route_id=r.id AND t.status='SCHEDULED' AND t.departure_time>?
                WHERE r.is_active=true
                GROUP BY r.id, r.origin_id, r.destination_id, r.origin, r.destination, r.distance, r.seat_count, r.suggested_price
                ORDER BY trip_count DESC, r.origin, r.destination
                """, this::mapRouteCard, System.currentTimeMillis());
        return collapseBidirectionalRouteCards(cards);
    }

    static List<RouteCard> collapseBidirectionalRouteCards(List<RouteCard> cards) {
        Map<String, RouteCardAggregate> byPair = new LinkedHashMap<>();
        for (RouteCard card : cards) {
            String key = bidirectionalRouteKey(card.origin(), card.destination());
            byPair.merge(key, RouteCardAggregate.of(card), RouteCardAggregate::merge);
        }
        return byPair.values().stream()
                .map(RouteCardAggregate::toRouteCard)
                .sorted(UserWebService::compareRouteCards)
                .toList();
    }

    private static String bidirectionalRouteKey(String origin, String destination) {
        String left = normalizeRoutePart(origin);
        String right = normalizeRoutePart(destination);
        return left.compareTo(right) <= 0 ? left + "|" + right : right + "|" + left;
    }

    private static String normalizeRoutePart(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static int compareRouteCards(RouteCard left, RouteCard right) {
        int tripCount = Integer.compare(safeTripCount(right.tripCount()), safeTripCount(left.tripCount()));
        if (tripCount != 0) {
            return tripCount;
        }
        int departure = compareNullableLong(left.nextDepartureTime(), right.nextDepartureTime());
        if (departure != 0) {
            return departure;
        }
        int origin = left.origin().compareToIgnoreCase(right.origin());
        if (origin != 0) {
            return origin;
        }
        return left.destination().compareToIgnoreCase(right.destination());
    }

    private static int compareNullableLong(Long left, Long right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return Long.compare(left, right);
    }

    private static RouteCard betterDisplayRoute(RouteCard current, RouteCard candidate) {
        if (current == null) {
            return candidate;
        }
        int tripCount = Integer.compare(safeTripCount(candidate.tripCount()), safeTripCount(current.tripCount()));
        if (tripCount > 0) {
            return candidate;
        }
        if (tripCount < 0) {
            return current;
        }
        int departure = compareNullableLong(candidate.nextDepartureTime(), current.nextDepartureTime());
        if (departure < 0) {
            return candidate;
        }
        if (departure > 0) {
            return current;
        }
        long candidateId = candidate.id() == null ? Long.MAX_VALUE : candidate.id();
        long currentId = current.id() == null ? Long.MAX_VALUE : current.id();
        return candidateId < currentId ? candidate : current;
    }

    private static BigDecimal minPrice(BigDecimal current, BigDecimal candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return current.compareTo(candidate) <= 0 ? current : candidate;
    }

    private static Long minTime(Long current, Long candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return Math.min(current, candidate);
    }

    private static int safeTripCount(Integer tripCount) {
        return tripCount == null ? 0 : tripCount;
    }

    private static int safeSeatCount(Integer seatCount) {
        return seatCount == null ? 0 : seatCount;
    }

    private static final class RouteCardAggregate {
        private RouteCard displayRoute;
        private final Map<Integer, RouteSeatOptionAggregate> seatOptions = new LinkedHashMap<>();
        private BigDecimal suggestedPrice;
        private Long nextDepartureTime;
        private int tripCount;

        static RouteCardAggregate of(RouteCard card) {
            RouteCardAggregate aggregate = new RouteCardAggregate();
            aggregate.add(card);
            return aggregate;
        }

        RouteCardAggregate merge(RouteCardAggregate other) {
            other.seatOptions.values().forEach(option -> addOption(option.toRouteCard(other.displayRoute)));
            displayRoute = betterDisplayRoute(displayRoute, other.displayRoute);
            suggestedPrice = minPrice(suggestedPrice, other.suggestedPrice);
            nextDepartureTime = minTime(nextDepartureTime, other.nextDepartureTime);
            tripCount += other.tripCount;
            return this;
        }

        private void add(RouteCard card) {
            displayRoute = betterDisplayRoute(displayRoute, card);
            suggestedPrice = minPrice(suggestedPrice, card.suggestedPrice());
            nextDepartureTime = minTime(nextDepartureTime, card.nextDepartureTime());
            tripCount += safeTripCount(card.tripCount());
            addOption(card);
        }

        private void addOption(RouteCard card) {
            seatOptions.merge(safeSeatCount(card.seatCount()), RouteSeatOptionAggregate.of(card), RouteSeatOptionAggregate::merge);
        }

        RouteCard toRouteCard() {
            List<RouteSeatOption> options = seatOptions.values().stream()
                    .map(RouteSeatOptionAggregate::toRouteSeatOption)
                    .sorted((left, right) -> Integer.compare(safeSeatCount(right.seatCount()), safeSeatCount(left.seatCount())))
                    .toList();
            return new RouteCard(
                    displayRoute.id(),
                    displayRoute.origin(),
                    displayRoute.destination(),
                    displayRoute.distance(),
                    displayRoute.seatCount(),
                    suggestedPrice,
                    nextDepartureTime,
                    tripCount,
                    options
            );
        }
    }

    private static final class RouteSeatOptionAggregate {
        private final Integer seatCount;
        private BigDecimal suggestedPrice;
        private Long nextDepartureTime;
        private int tripCount;

        private RouteSeatOptionAggregate(Integer seatCount) {
            this.seatCount = seatCount;
        }

        static RouteSeatOptionAggregate of(RouteCard card) {
            RouteSeatOptionAggregate aggregate = new RouteSeatOptionAggregate(card.seatCount());
            aggregate.suggestedPrice = card.suggestedPrice();
            aggregate.nextDepartureTime = card.nextDepartureTime();
            aggregate.tripCount = safeTripCount(card.tripCount());
            return aggregate;
        }

        RouteSeatOptionAggregate merge(RouteSeatOptionAggregate other) {
            suggestedPrice = minPrice(suggestedPrice, other.suggestedPrice);
            nextDepartureTime = minTime(nextDepartureTime, other.nextDepartureTime);
            tripCount += other.tripCount;
            return this;
        }

        RouteCard toRouteCard(RouteCard displayRoute) {
            return new RouteCard(
                    displayRoute.id(),
                    displayRoute.origin(),
                    displayRoute.destination(),
                    displayRoute.distance(),
                    seatCount,
                    suggestedPrice,
                    nextDepartureTime,
                    tripCount
            );
        }

        RouteSeatOption toRouteSeatOption() {
            return new RouteSeatOption(seatCount, suggestedPrice, nextDepartureTime, tripCount);
        }
    }

    public List<TripView> upcomingTrips(int limit) {
        return jdbcTemplate.query(baseTripSql() + """
                WHERE t.status='SCHEDULED' AND t.departure_time>?
                ORDER BY t.departure_time
                LIMIT ?
                """, this::mapTrip, System.currentTimeMillis(), limit);
    }

    public List<TripView> searchTrips(String origin, String destination, LocalDate date, Integer totalSeats) {
        ProvinceLocation originLocation = routeCatalogService.canonicalLocation(origin, origin);
        ProvinceLocation destinationLocation = routeCatalogService.canonicalLocation(destination, destination);
        long searchDayStart = date.atStartOfDay(VN_ZONE).toInstant().toEpochMilli();
        StringBuilder sql = new StringBuilder(baseTripSql()).append("""
                WHERE t.status='SCHEDULED'
                  AND t.departure_time>=?
                  AND t.departure_time<?
                  AND t.departure_time>?
                """);
        List<Object> args = new ArrayList<>();
        args.add(searchDayStart);
        args.add(searchDayStart + DAY_MS);
        args.add(System.currentTimeMillis());
        if (totalSeats != null && (totalSeats == 24 || totalSeats == 34)) {
            sql.append(" AND b.total_seats=?");
            args.add(totalSeats);
        }
        sql.append(" ORDER BY t.departure_time");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
                    ProvinceLocation tripOrigin = routeCatalogService.canonicalLocation(rs.getString("origin_id"), rs.getString("origin"));
                    ProvinceLocation tripDestination = routeCatalogService.canonicalLocation(rs.getString("destination_id"), rs.getString("destination"));
                    if (!sameLocation(tripOrigin, originLocation) || !sameLocation(tripDestination, destinationLocation)) {
                        return null;
                    }
                    return mapTrip(rs, rowNum);
                }, args.toArray())
                .stream()
                .filter(Objects::nonNull)
                .toList();
    }

    public TripView findTrip(long tripId) {
        List<TripView> trips = jdbcTemplate.query(baseTripSql() + " WHERE t.id=?", this::mapTrip, tripId);
        return trips.isEmpty() ? null : trips.get(0);
    }

    public List<SeatView> seatsForTrip(long tripId) {
        return jdbcTemplate.query("""
                SELECT s.*,
                       CASE
                           WHEN EXISTS (
                               SELECT 1
                               FROM tickets tk
                               LEFT JOIN seats booked_seat ON booked_seat.id=tk.seat_id
                               WHERE tk.trip_id=t.id
                                 AND tk.status IN ('PENDING_PAYMENT','PENDING','CONFIRMED','CHECKED_IN')
                                 AND (tk.seat_id=s.id OR booked_seat.seat_number=s.seat_number)
                           )
                           OR EXISTS (
                               SELECT 1
                               FROM trip_seats ts
                               LEFT JOIN seats held_seat ON held_seat.id=ts.seat_id
                               WHERE ts.trip_id=t.id
                                 AND ts.status IN ('BOOKED','CONFIRMED','CHECKED_IN')
                                 AND (ts.seat_id=s.id OR held_seat.seat_number=s.seat_number)
                           )
                           THEN true
                           ELSE false
                       END AS booked
                FROM trips t
                JOIN seats s ON s.bus_id=t.bus_id
                WHERE t.id=?
                ORDER BY s.floor,s.row_index,s.column_index,s.id
                """, this::mapSeat, tripId);
    }

    @Transactional
    public CheckoutResult bookSeats(long userId, long tripId, List<Long> seatIds) {
        return userBookingService.bookSeats(userId, tripId, seatIds);
    }

    public PaymentView paymentForUser(String paymentId, long userId) {
        PaymentView payment = jdbcTemplate.query("""
                SELECT id,user_id,trip_id,seat_id,amount,provider,status,payment_url,qr_content,qr_image_base64,qr_mime_type,created_at,updated_at,expires_at
                FROM payments
                WHERE id=? AND user_id=?
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            return mapPayment(rs, List.of());
        }, paymentId, userId);
        if (payment == null) {
            return null;
        }
        return new PaymentView(
                payment.id(),
                payment.userId(),
                payment.tripId(),
                payment.seatId(),
                payment.amount(),
                payment.provider(),
                payment.status(),
                payment.paymentUrl(),
                payment.qrContent(),
                payment.qrImageBase64(),
                payment.qrMimeType(),
                payment.createdAt(),
                payment.updatedAt(),
                payment.expiresAt(),
                ticketsByPayment(paymentId, userId)
        );
    }

    public List<TicketView> ticketsForUser(long userId) {
        return jdbcTemplate.query(ticketSql() + " WHERE tk.user_id=? ORDER BY tk.booking_time DESC", this::mapTicket, userId);
    }

    public List<TicketView> upcomingTicketsForUser(long userId, int limit) {
        return jdbcTemplate.query(ticketSql() + """
                WHERE tk.user_id=?
                  AND tk.status IN ('CONFIRMED','CHECKED_IN')
                  AND t.departure_time>?
                ORDER BY t.departure_time
                LIMIT ?
                """, this::mapTicket, userId, System.currentTimeMillis(), limit);
    }

    public TicketView ticketForUser(long ticketId, long userId) {
        List<TicketView> tickets = jdbcTemplate.query(ticketSql() + " WHERE tk.id=? AND tk.user_id=?", this::mapTicket, ticketId, userId);
        return tickets.isEmpty() ? null : tickets.get(0);
    }

    public TicketView ticket(long ticketId) {
        List<TicketView> tickets = jdbcTemplate.query(ticketSql() + " WHERE tk.id=?", this::mapTicket, ticketId);
        return tickets.isEmpty() ? null : tickets.get(0);
    }

    public void cancelTicket(long ticketId, long userId) {
        int rows = cancelTicket(ticketId, userId, "Nguoi dung huy ve", null);
        if (rows == 0) {
            throw new IllegalStateException("Chi co the huy ve chua thanh toan hoac thanh toan khong thanh cong");
        }
    }

    public int cancelTicket(long ticketId, long userId, String reason, Double refundAmount) {
        int rows = jdbcTemplate.update("""
                UPDATE tickets
                SET status='CANCELLED',
                    cancellation_reason=?,
                    refund_amount=?,
                    refund_status='NONE',
                    updated_at=?
                WHERE id=?
                  AND user_id=?
                  AND status IN ('PENDING','PENDING_PAYMENT','PAYMENT_FAILED','FAILED','EXPIRED')
                """, StringUtils.hasText(reason) ? reason.trim() : "Nguoi dung huy ve", refundAmount, System.currentTimeMillis(), ticketId, userId);
        return rows;
    }

    private List<TicketView> ticketsByPayment(String paymentId, long userId) {
        return jdbcTemplate.query(ticketSql() + " WHERE tk.payment_id=? AND tk.user_id=? ORDER BY tk.id", this::mapTicket, paymentId, userId);
    }

    private String baseTripSql() {
        return """
                SELECT t.*, r.origin_id, r.destination_id, r.origin, r.destination, r.distance,
                       b.bus_name, b.total_seats, b.license_plate,
                       (b.total_seats - COALESCE(booked.booked_seats, 0)) AS available_seats
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

    private String ticketSql() {
        return """
                SELECT tk.*, u.name, u.email, u.phone,
                       t.departure_time, t.arrival_time, t.price, t.trip_date, t.status trip_status,
                       r.id route_id, r.origin_id, r.destination_id, r.origin, r.destination, r.distance,
                       b.id bus_id2, b.bus_name, b.total_seats, b.license_plate,
                       s.seat_number, s.floor, s.row_index, s.column_index, s.is_window, s.is_aisle, s.seat_type
                FROM tickets tk
                JOIN users u ON u.id=tk.user_id
                JOIN trips t ON t.id=tk.trip_id
                JOIN routes r ON r.id=t.route_id
                JOIN buses b ON b.id=t.bus_id
                JOIN seats s ON s.id=tk.seat_id
                """;
    }

    private RouteCard mapRouteCard(ResultSet rs, int rowNum) throws SQLException {
        ProvinceLocation origin = routeCatalogService.canonicalLocation(rs.getString("origin_id"), rs.getString("origin"));
        ProvinceLocation destination = routeCatalogService.canonicalLocation(rs.getString("destination_id"), rs.getString("destination"));
        return new RouteCard(
                rs.getLong("id"),
                origin.name(),
                destination.name(),
                rs.getInt("distance"),
                rs.getInt("seat_count"),
                rs.getBigDecimal("suggested_price"),
                getNullableLong(rs, "next_departure_time"),
                rs.getInt("trip_count")
        );
    }

    private TripView mapTrip(ResultSet rs, int rowNum) throws SQLException {
        ProvinceLocation origin = routeCatalogService.canonicalLocation(rs.getString("origin_id"), rs.getString("origin"));
        ProvinceLocation destination = routeCatalogService.canonicalLocation(rs.getString("destination_id"), rs.getString("destination"));
        return new TripView(
                rs.getLong("id"),
                rs.getLong("route_id"),
                rs.getLong("bus_id"),
                origin.name(),
                destination.name(),
                rs.getInt("distance"),
                rs.getLong("departure_time"),
                rs.getLong("arrival_time"),
                rs.getLong("trip_date"),
                rs.getBigDecimal("price"),
                rs.getString("status"),
                rs.getString("bus_name"),
                rs.getString("license_plate"),
                rs.getInt("total_seats"),
                rs.getInt("available_seats")
        );
    }

    private SeatView mapSeat(ResultSet rs, int rowNum) throws SQLException {
        return new SeatView(
                rs.getLong("id"),
                rs.getLong("bus_id"),
                rs.getString("seat_number"),
                rs.getInt("floor"),
                rs.getInt("row_index"),
                rs.getInt("column_index"),
                rs.getBoolean("is_window"),
                rs.getBoolean("is_aisle"),
                rs.getString("seat_type"),
                rs.getBoolean("booked")
        );
    }

    private TicketView mapTicket(ResultSet rs, int rowNum) throws SQLException {
        ProvinceLocation origin = routeCatalogService.canonicalLocation(rs.getString("origin_id"), rs.getString("origin"));
        ProvinceLocation destination = routeCatalogService.canonicalLocation(rs.getString("destination_id"), rs.getString("destination"));
        TripView trip = new TripView(
                rs.getLong("trip_id"),
                rs.getLong("route_id"),
                rs.getLong("bus_id2"),
                origin.name(),
                destination.name(),
                rs.getInt("distance"),
                rs.getLong("departure_time"),
                rs.getLong("arrival_time"),
                rs.getLong("trip_date"),
                rs.getBigDecimal("price"),
                rs.getString("trip_status"),
                rs.getString("bus_name"),
                rs.getString("license_plate"),
                rs.getInt("total_seats"),
                0
        );
        SeatView seat = new SeatView(
                rs.getLong("seat_id"),
                rs.getLong("bus_id2"),
                rs.getString("seat_number"),
                rs.getInt("floor"),
                rs.getInt("row_index"),
                rs.getInt("column_index"),
                rs.getBoolean("is_window"),
                rs.getBoolean("is_aisle"),
                rs.getString("seat_type"),
                true
        );
        String status = rs.getString("status");
        boolean paid = "CONFIRMED".equalsIgnoreCase(status) || "CHECKED_IN".equalsIgnoreCase(status);
        String qrContent = paid ? ticketQrContent(rs.getLong("id")) : "";
        return new TicketView(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getLong("trip_id"),
                rs.getLong("seat_id"),
                rs.getLong("booking_time"),
                status,
                rs.getString("cancellation_reason"),
                rs.getBigDecimal("refund_amount"),
                rs.getString("refund_status"),
                rs.getString("payment_id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("phone"),
                trip,
                seat,
                qrContent,
                paid ? qrCodeService.createPngBase64(qrContent) : "",
                paid ? "image/png" : ""
        );
    }

    private PaymentView mapPayment(ResultSet rs, List<TicketView> tickets) throws SQLException {
        return new PaymentView(
                rs.getString("id"),
                getNullableLong(rs, "user_id"),
                getNullableLong(rs, "trip_id"),
                getNullableLong(rs, "seat_id"),
                rs.getBigDecimal("amount"),
                rs.getString("provider"),
                rs.getString("status"),
                rs.getString("payment_url"),
                rs.getString("qr_content"),
                rs.getString("qr_image_base64"),
                rs.getString("qr_mime_type"),
                rs.getLong("created_at"),
                getNullableLong(rs, "updated_at"),
                getNullableLong(rs, "expires_at"),
                tickets
        );
    }

    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private boolean sameLocation(ProvinceLocation actual, ProvinceLocation requested) {
        if (StringUtils.hasText(actual.id()) && StringUtils.hasText(requested.id())) {
            return text(actual.id()).equals(text(requested.id()));
        }
        return routeCatalogService.locationMatches(actual.name(), requested.name());
    }

    private String ticketQrContent(Long ticketId) {
        return "BUSBOOKING-TICKET:" + ticketId;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    public LocalDate defaultSearchDate() {
        return LocalDate.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), VN_ZONE);
    }
}
