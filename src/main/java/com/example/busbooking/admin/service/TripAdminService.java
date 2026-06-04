package com.example.busbooking.admin.service;

import com.example.busbooking.admin.model.StaffOption;
import com.example.busbooking.admin.model.TripDto;
import com.example.busbooking.admin.model.TripForm;
import com.example.busbooking.admin.model.TripSeatView;
import com.example.busbooking.admin.util.BusRouteMatcher;
import com.example.busbooking.shared.service.RouteCatalogService;
import com.example.busbooking.shared.service.RouteCatalogService.ProvinceLocation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripAdminService {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Bangkok");
    private static final long MIN_STAFF_TRIP_GAP_MS = 60 * 60 * 1000L;
    private static final int MAX_STAFF_TRIPS_PER_DAY = 2;
    private static final int ADMIN_VISIBLE_DAYS = 5;

    private final JdbcTemplate jdbcTemplate;
    private final RouteCatalogService routeCatalogService;

    public TripAdminService(JdbcTemplate jdbcTemplate, RouteCatalogService routeCatalogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.routeCatalogService = routeCatalogService;
    }

    public LocalDate today() {
        return LocalDate.now(VN_ZONE);
    }

    public LocalDate lastVisibleDate() {
        return today().plusDays(ADMIN_VISIBLE_DAYS - 1L);
    }

    public LocalDate normalizeAdminDate(LocalDate date) {
        LocalDate today = today();
        LocalDate last = today.plusDays(ADMIN_VISIBLE_DAYS - 1L);
        if (date == null || date.isBefore(today)) {
            return today;
        }
        if (date.isAfter(last)) {
            return last;
        }
        return date;
    }

    public List<TripDto> findByDate(LocalDate date) {
        LocalDate selectedDate = normalizeAdminDate(date);
        long start = selectedDate.atStartOfDay(VN_ZONE).toInstant().toEpochMilli();
        long end = selectedDate.plusDays(1).atStartOfDay(VN_ZONE).toInstant().toEpochMilli();
        return jdbcTemplate.query(baseSelect() + """
                WHERE t.departure_time>=?
                  AND t.departure_time<?
                  AND t.status <> 'CANCELLED'
                ORDER BY t.departure_time ASC, t.id ASC
                """, this::mapTrip, start, end);
    }

    public TripDto findByDocumentId(String documentId) {
        return jdbcTemplate.query(baseSelect() + " WHERE t.id = ?", rs -> {
            if (!rs.next()) throw new IllegalArgumentException("Khong tim thay chuyen");
            return mapTrip(rs, 1);
        }, SqlRows.parseDocumentId(documentId));
    }

    @Transactional
    public void create(TripForm form) {
        long now = System.currentTimeMillis();
        validateRouteBusMatch(form.getRouteId(), form.getBusId());
        validateStaffAvailability(null, form.getStaffId(), form.getDepartureTime(), form.getArrivalTime());
        double price = normalizePrice(form.getRouteId(), form.getPrice());
        long tripDate = dayStart(form.getDepartureTime());
        jdbcTemplate.update("INSERT INTO trips(route_id,bus_id,departure_time,arrival_time,price,trip_date,status,created_at) VALUES (?,?,?,?,?,?,?,?)",
                form.getRouteId(), form.getBusId(), form.getDepartureTime(), form.getArrivalTime(), price, tripDate, status(form.getStatus()), now);
        Long tripId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        assignStaff(tripId, form.getStaffId(), form.getBusId(), now);
    }

    @Transactional
    public void update(String documentId, TripForm form) {
        Long tripId = SqlRows.parseDocumentId(documentId);
        long now = System.currentTimeMillis();
        validateRouteBusMatch(form.getRouteId(), form.getBusId());
        validateStaffAvailability(tripId, form.getStaffId(), form.getDepartureTime(), form.getArrivalTime());
        double price = normalizePrice(form.getRouteId(), form.getPrice());
        long tripDate = dayStart(form.getDepartureTime());
        jdbcTemplate.update("UPDATE trips SET route_id=?,bus_id=?,departure_time=?,arrival_time=?,price=?,trip_date=?,status=?,updated_at=? WHERE id=?",
                form.getRouteId(), form.getBusId(), form.getDepartureTime(), form.getArrivalTime(), price, tripDate, status(form.getStatus()), now, tripId);
        assignStaff(tripId, form.getStaffId(), form.getBusId(), now);
    }

    @Transactional
    public void cancel(String documentId) {
        long now = System.currentTimeMillis();
        long tripId = SqlRows.parseDocumentId(documentId);
        jdbcTemplate.update("UPDATE trips SET status='CANCELLED', updated_at=? WHERE id=?", now, tripId);
        jdbcTemplate.update("UPDATE trip_staff_assignments SET status='INACTIVE', updated_at=? WHERE trip_id=? AND status='ACTIVE'", now, tripId);
    }

    public List<TripSeatView> findSeatViews(String documentId) {
        return jdbcTemplate.query("""
                        SELECT s.id, s.seat_number, s.floor, s.row_index, s.column_index,
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
                                   THEN 'BOOKED'
                                   ELSE 'AVAILABLE'
                               END AS status
                        FROM trips t
                        JOIN seats s ON s.bus_id = t.bus_id
                        WHERE t.id = ?
                        ORDER BY s.floor, s.row_index, s.column_index, s.id
                        """,
                (rs, rowNum) -> new TripSeatView(rs.getLong("id"), rs.getString("seat_number"), rs.getInt("floor"), rs.getInt("row_index"), rs.getInt("column_index"), rs.getString("status")),
                SqlRows.parseDocumentId(documentId));
    }

    public Long findAssignedStaffId(String documentId) {
        List<Long> values = jdbcTemplate.query("""
                        SELECT staff_id
                        FROM trip_staff_assignments
                        WHERE trip_id=? AND status='ACTIVE'
                        ORDER BY assigned_at DESC, id DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> rs.getLong("staff_id"),
                SqlRows.parseDocumentId(documentId));
        return values.isEmpty() ? null : values.get(0);
    }

    public List<StaffOption> findStaffOptions() {
        return findActiveStaff();
    }

    private List<StaffOption> findActiveStaff() {
        return jdbcTemplate.query("""
                        SELECT u.id, u.name, u.email, COALESCE(sp.staff_code, u.uid) AS staff_code
                        FROM users u
                        LEFT JOIN staff_profiles sp ON sp.user_id=u.id
                        WHERE u.role='STAFF'
                          AND u.is_blocked=false
                          AND (sp.id IS NULL OR sp.status='ACTIVE')
                        ORDER BY u.name, u.email
                        """,
                this::mapStaffOption);
    }

    private StaffOption mapStaffOption(ResultSet rs, int rowNum) throws SQLException {
        return new StaffOption(rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.getString("staff_code"));
    }

    private void validateStaffAvailability(Long currentTripId, Long staffId, Long departureTime, Long arrivalTime) {
        if (staffId == null || staffId <= 0 || departureTime == null || arrivalTime == null) {
            return;
        }
        String conflictReason = staffConflictReason(currentTripId, staffId, departureTime, arrivalTime);
        if (conflictReason != null) {
            throw new IllegalArgumentException(conflictReason);
        }
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM trip_staff_assignments tsa
                        JOIN trips t ON t.id=tsa.trip_id
                        WHERE tsa.staff_id=?
                          AND tsa.status='ACTIVE'
                          AND t.status NOT IN ('CANCELLED','COMPLETED')
                          AND (? IS NULL OR t.id<>?)
                          AND t.departure_time < ?
                          AND t.arrival_time > ?
                        """,
                Integer.class,
                staffId, currentTripId, currentTripId, arrivalTime, departureTime);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("Nhan vien da duoc gan chuyen khac trong khung gio nay");
        }
    }

    private String staffConflictReason(Long currentTripId, Long staffId, Long departureTime, Long arrivalTime) {
        long dayStart = dayStart(departureTime);
        long dayEnd = dayStart + 86_400_000L;
        Integer dailyTrips = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM trip_staff_assignments tsa
                        JOIN trips t ON t.id=tsa.trip_id
                        WHERE tsa.staff_id=?
                          AND tsa.status='ACTIVE'
                          AND t.status NOT IN ('CANCELLED','COMPLETED')
                          AND (? IS NULL OR t.id<>?)
                          AND t.departure_time>=?
                          AND t.departure_time<?
                        """,
                Integer.class,
                staffId, currentTripId, currentTripId, dayStart, dayEnd);
        if (dailyTrips != null && dailyTrips >= MAX_STAFF_TRIPS_PER_DAY) {
            return "Nhan vien chi duoc phan toi da 2 chuyen trong cung mot ngay";
        }
        Integer closeTrips = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM trip_staff_assignments tsa
                        JOIN trips t ON t.id=tsa.trip_id
                        WHERE tsa.staff_id=?
                          AND tsa.status='ACTIVE'
                          AND t.status NOT IN ('CANCELLED','COMPLETED')
                          AND (? IS NULL OR t.id<>?)
                          AND t.departure_time < ?
                          AND t.arrival_time > ?
                        """,
                Integer.class,
                staffId, currentTripId, currentTripId, arrivalTime + MIN_STAFF_TRIP_GAP_MS, departureTime - MIN_STAFF_TRIP_GAP_MS);
        if (closeTrips != null && closeTrips > 0) {
            return "Nhan vien da co chuyen khac trung gio hoac qua sat gio";
        }
        return null;
    }

    private long dayStart(long millis) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(millis), VN_ZONE)
                .atStartOfDay(VN_ZONE)
                .toInstant()
                .toEpochMilli();
    }

    private void assignStaff(Long tripId, Long staffId, Long busId, long now) {
        if (tripId == null) {
            return;
        }
        jdbcTemplate.update("UPDATE trip_staff_assignments SET status='INACTIVE', updated_at=? WHERE trip_id=? AND status='ACTIVE'", now, tripId);
        if (staffId == null || staffId <= 0) {
            return;
        }
        Long companyId = findCompanyIdByBus(busId);
        jdbcTemplate.update("""
                        INSERT INTO trip_staff_assignments(trip_id, staff_id, bus_id, company_id, role_on_trip, status, assigned_at, updated_at)
                        VALUES (?, ?, ?, ?, 'STAFF', 'ACTIVE', ?, ?)
                        ON DUPLICATE KEY UPDATE
                            bus_id=VALUES(bus_id),
                            company_id=VALUES(company_id),
                            role_on_trip='STAFF',
                            status='ACTIVE',
                            assigned_at=VALUES(assigned_at),
                            updated_at=VALUES(updated_at)
                        """,
                tripId, staffId, busId, companyId, now, now);
    }

    private void validateRouteBusMatch(Long routeId, Long busId) {
        if (routeId == null || busId == null) {
            return;
        }
        List<RouteBusMatch> values = jdbcTemplate.query("""
                        SELECT r.origin_id, r.destination_id, r.seat_count,
                               b.bus_name, b.license_plate, b.total_seats
                        FROM routes r
                        JOIN buses b ON b.id=?
                        WHERE r.id=?
                        """,
                (rs, rowNum) -> new RouteBusMatch(rs.getString("origin_id"), rs.getString("destination_id"), rs.getInt("seat_count"), rs.getString("bus_name"), rs.getString("license_plate"), rs.getInt("total_seats")),
                busId,
                routeId);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Khong tim thay tuyen hoac xe");
        }
        RouteBusMatch match = values.get(0);
        if (match.routeSeatCount() != match.busSeatCount()) {
            throw new IllegalArgumentException("Tuyen da chon khong khop loai xe 24/34 ghe");
        }
        String busProvinceId = BusRouteMatcher.serviceProvinceId(match.busName(), match.licensePlate());
        if (!BusRouteMatcher.canServeRoute(busProvinceId, match.originId(), match.destinationId())) {
            throw new IllegalArgumentException("Xe da chon khong thuoc diem di hoac diem den cua tuyen");
        }
    }

    private double normalizePrice(Long routeId, Double formPrice) {
        if (formPrice != null && formPrice > 0) {
            return formPrice;
        }
        List<Double> values = jdbcTemplate.query("SELECT suggested_price FROM routes WHERE id=? LIMIT 1",
                (rs, rowNum) -> rs.getDouble(1),
                routeId);
        return values.isEmpty() ? 0.0 : values.get(0);
    }

    private Long findCompanyIdByBus(Long busId) {
        if (busId == null) {
            return null;
        }
        List<Long> values = jdbcTemplate.query("SELECT company_id FROM buses WHERE id=? LIMIT 1",
                (rs, rowNum) -> {
                    long value = rs.getLong("company_id");
                    return rs.wasNull() ? null : value;
                },
                busId);
        return values.isEmpty() ? null : values.get(0);
    }

    private String baseSelect() {
        return """
                SELECT t.*, r.origin_id, r.destination_id, r.origin, r.destination, b.license_plate, b.total_seats,
                       active_staff.staff_names,
                       (b.total_seats - COALESCE(booked.booked_seats, 0)) AS available_seats
                FROM trips t
                JOIN routes r ON r.id = t.route_id
                JOIN buses b ON b.id = t.bus_id
                LEFT JOIN (
                    SELECT trip_id, COUNT(*) AS booked_seats
                    FROM tickets
                    WHERE status IN ('PENDING_PAYMENT','PENDING','CONFIRMED','CHECKED_IN')
                    GROUP BY trip_id
                ) booked ON booked.trip_id = t.id
                LEFT JOIN (
                    SELECT tsa.trip_id, GROUP_CONCAT(u.name ORDER BY u.name SEPARATOR ', ') AS staff_names
                    FROM trip_staff_assignments tsa
                    JOIN users u ON u.id = tsa.staff_id
                    WHERE tsa.status='ACTIVE'
                    GROUP BY tsa.trip_id
                ) active_staff ON active_staff.trip_id = t.id
                """;
    }

    private TripDto mapTrip(ResultSet rs, int rowNum) throws SQLException {
        Long id = rs.getLong("id");
        int totalSeats = rs.getInt("total_seats");
        int availableSeats = Math.max(0, rs.getInt("available_seats"));
        ProvinceLocation origin = routeCatalogService.canonicalLocation(rs.getString("origin_id"), rs.getString("origin"));
        ProvinceLocation destination = routeCatalogService.canonicalLocation(rs.getString("destination_id"), rs.getString("destination"));
        return new TripDto(SqlRows.documentId(id), id, rs.getLong("route_id"), rs.getLong("bus_id"), origin.name() + " -> " + destination.name(), rs.getString("license_plate"), rs.getString("staff_names"), rs.getLong("departure_time"), rs.getLong("arrival_time"), rs.getDouble("price"), rs.getLong("trip_date"), rs.getString("status"), availableSeats <= 0 ? "FULL" : "AVAILABLE", availableSeats, totalSeats, rs.getLong("created_at"));
    }

    private String status(String value) {
        return value == null || value.isBlank() ? "SCHEDULED" : value.trim().toUpperCase();
    }

    private record RouteBusMatch(String originId, String destinationId, int routeSeatCount, String busName, String licensePlate, int busSeatCount) {}
}
