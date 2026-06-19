package com.example.busbooking.admin.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RollingTripGeneratorService {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Bangkok");
    private static final long DAY_MS = 86_400_000L;
    private static final long MIN_STAFF_TRIP_GAP_MS = 3_600_000L;
    private static final long REFRESH_TTL_MS = 60_000L;
    private static final int ROLLING_DAYS = 5;
    private static final List<TripTemplate> TRIP_TEMPLATES = List.of(
            new TripTemplate("ha_noi", "da_nang", "29B-45678", 25_200_000L),
            new TripTemplate("da_nang", "ha_noi", "43B-24680", 54_000_000L),
            new TripTemplate("ha_noi", "da_nang", "29K-2401", 32_400_000L),
            new TripTemplate("da_nang", "ha_noi", "43K-2401", 61_200_000L),
            new TripTemplate("ha_noi", "lam_dong", "30F-56789", 25_200_000L),
            new TripTemplate("lam_dong", "ha_noi", "49B-67890", 57_600_000L),
            new TripTemplate("ha_noi", "lam_dong", "30G-55667", 36_000_000L),
            new TripTemplate("lam_dong", "ha_noi", "49K-2401", 68_400_000L),
            new TripTemplate("ha_noi", "ho_chi_minh", "30H-77889", 25_200_000L),
            new TripTemplate("ho_chi_minh", "ha_noi", "51B-12345", 61_200_000L),
            new TripTemplate("ha_noi", "ho_chi_minh", "29F-33445", 32_400_000L),
            new TripTemplate("ho_chi_minh", "ha_noi", "51C-10203", 68_400_000L),
            new TripTemplate("da_nang", "lam_dong", "43C-22334", 25_200_000L),
            new TripTemplate("lam_dong", "da_nang", "49C-66778", 54_000_000L),
            new TripTemplate("da_nang", "lam_dong", "43D-44556", 36_000_000L),
            new TripTemplate("lam_dong", "da_nang", "49D-88990", 64_800_000L),
            new TripTemplate("da_nang", "ho_chi_minh", "43E-2401", 25_200_000L),
            new TripTemplate("ho_chi_minh", "da_nang", "51E-2401", 57_600_000L),
            new TripTemplate("da_nang", "ho_chi_minh", "43L-2401", 32_400_000L),
            new TripTemplate("ho_chi_minh", "da_nang", "51M-2401", 64_800_000L),
            new TripTemplate("lam_dong", "ho_chi_minh", "49B-67890", 25_200_000L),
            new TripTemplate("ho_chi_minh", "lam_dong", "51C-10203", 54_000_000L),
            new TripTemplate("lam_dong", "ho_chi_minh", "49K-2401", 36_000_000L),
            new TripTemplate("ho_chi_minh", "lam_dong", "51N-2401", 64_800_000L),
            new TripTemplate("quang_tri", "ho_chi_minh", "74B-2401", 25_200_000L),
            new TripTemplate("ho_chi_minh", "quang_tri", "51D-2401", 61_200_000L),
            new TripTemplate("quang_tri", "ho_chi_minh", "74K-2401", 32_400_000L),
            new TripTemplate("ho_chi_minh", "quang_tri", "51L-2401", 68_400_000L),
            new TripTemplate("thua_thien_hue", "ha_noi", "75B-2401", 25_200_000L),
            new TripTemplate("ha_noi", "thua_thien_hue", "29B-11223", 54_000_000L),
            new TripTemplate("thua_thien_hue", "ha_noi", "75K-2401", 32_400_000L),
            new TripTemplate("ha_noi", "thua_thien_hue", "29L-2401", 61_200_000L)
    );

    private final JdbcTemplate jdbcTemplate;
    private volatile long lastRefreshAt;

    public RollingTripGeneratorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void generateOnStartup() {
        generateUpcomingTripsIfStale();
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Bangkok")
    public void generateOnSchedule() {
        generateUpcomingTrips();
    }

    @Transactional
    public synchronized Map<String, Object> generateUpcomingTripsIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshAt < REFRESH_TTL_MS) {
            return Map.of("code", "00", "message", "Rolling trips already fresh", "inserted", 0);
        }
        return generateUpcomingTrips();
    }

    @Transactional
    public synchronized Map<String, Object> generateUpcomingTrips() {
        long today = LocalDate.now(VN_ZONE).atStartOfDay(VN_ZONE).toInstant().toEpochMilli();
        long now = System.currentTimeMillis();
        Long defaultCompanyId = findDefaultCompanyId();
        int insertedTrips = 0;
        int autoAssignedStaff = 0;
        int existingTrips = 0;

        for (TripTemplate template : TRIP_TEMPLATES) {
            TemplateContext context = findTemplateContext(template, defaultCompanyId);
            if (context == null) {
                continue;
            }
            for (int day = 0; day < ROLLING_DAYS; day++) {
                long tripDate = today + day * DAY_MS;
                long departureTime = tripDate + template.departMs();
                long arrivalTime = departureTime + context.durationMs();
                TripRef trip = findTrip(context.routeId(), context.busId(), departureTime);
                if (trip == null) {
                    Long tripId = insertTrip(context, tripDate, departureTime, arrivalTime, now);
                    insertedTrips++;
                    autoAssignedStaff += assignStaffIfNeverAssigned(tripId, context, departureTime, arrivalTime, now);
                } else {
                    // Existing trips belong to the management workflow. Never update their
                    // status, price, schedule or staff assignment during rolling generation.
                    existingTrips++;
                }
            }
        }

        lastRefreshAt = now;
        return Map.of(
                "code", "00",
                "message", "Missing rolling trips generated",
                "days", ROLLING_DAYS,
                "inserted", insertedTrips,
                "autoAssignedStaff", autoAssignedStaff,
                "existing", existingTrips
        );
    }

    private TemplateContext findTemplateContext(TripTemplate template, Long defaultCompanyId) {
        List<TemplateContext> values = jdbcTemplate.query("""
                SELECT r.id AS route_id,
                       b.id AS bus_id,
                       COALESCE(b.company_id, ?) AS company_id,
                       r.suggested_price,
                       r.duration_ms
                FROM buses b
                JOIN routes r ON r.origin_id=? AND r.destination_id=? AND r.seat_count=b.total_seats AND r.is_active=true
                WHERE b.license_plate=? AND b.is_active=true
                ORDER BY r.id, b.id
                LIMIT 1
                """, this::mapTemplateContext,
                defaultCompanyId,
                template.originId(),
                template.destinationId(),
                template.licensePlate());
        return values.isEmpty() ? null : values.get(0);
    }

    private TemplateContext mapTemplateContext(ResultSet rs, int rowNum) throws SQLException {
        Long companyId = nullableLong(rs, "company_id");
        return new TemplateContext(
                rs.getLong("route_id"),
                rs.getLong("bus_id"),
                companyId,
                rs.getDouble("suggested_price"),
                rs.getLong("duration_ms")
        );
    }

    private Long insertTrip(TemplateContext context, long tripDate, long departureTime, long arrivalTime, long now) {
        jdbcTemplate.update("""
                INSERT INTO trips(company_id, route_id, bus_id, departure_time, arrival_time, price, trip_date, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'SCHEDULED', ?)
                """, context.companyId(), context.routeId(), context.busId(), departureTime, arrivalTime, context.price(), tripDate, now);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private TripRef findTrip(long routeId, long busId, long departureTime) {
        List<TripRef> values = jdbcTemplate.query("""
                SELECT id, status
                FROM trips
                WHERE route_id=? AND bus_id=? AND departure_time=?
                ORDER BY FIELD(status, 'SCHEDULED', 'RUNNING', 'DEPARTED', 'COMPLETED', 'CANCELLED'), id
                LIMIT 1
                """, (rs, rowNum) -> new TripRef(rs.getLong("id"), rs.getString("status")), routeId, busId, departureTime);
        return values.isEmpty() ? null : values.get(0);
    }

    private int assignStaffIfNeverAssigned(Long tripId, TemplateContext context, long departureTime, long arrivalTime, long now) {
        Integer assignmentHistory = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM trip_staff_assignments
                WHERE trip_id=?
                """, Integer.class, tripId);
        if (assignmentHistory != null && assignmentHistory > 0) {
            return 0;
        }
        Long staffId = findAvailableStaff(context.companyId(), departureTime, arrivalTime);
        if (staffId == null) {
            return 0;
        }
        jdbcTemplate.update("""
                INSERT INTO trip_staff_assignments(trip_id, staff_id, bus_id, company_id, role_on_trip, status, assigned_at, updated_at)
                VALUES (?, ?, ?, ?, 'STAFF', 'ACTIVE', ?, ?)
                """, tripId, staffId, context.busId(), context.companyId(), now, now);
        return 1;
    }

    private Long findAvailableStaff(Long companyId, long departureTime, long arrivalTime) {
        long dayStart = dayStart(departureTime);
        long dayEnd = dayStart + DAY_MS;
        List<Long> values = jdbcTemplate.query("""
                SELECT u.id
                FROM users u
                LEFT JOIN staff_profiles sp ON sp.user_id=u.id
                WHERE u.role='STAFF'
                  AND u.is_blocked=false
                  AND (sp.status IS NULL OR sp.status='ACTIVE')
                  AND (? IS NULL OR sp.company_id IS NULL OR sp.company_id=?)
                  AND (
                      SELECT COUNT(*)
                      FROM trip_staff_assignments tsa
                      JOIN trips t ON t.id=tsa.trip_id
                      WHERE tsa.staff_id=u.id
                        AND tsa.status='ACTIVE'
                        AND t.status NOT IN ('CANCELLED','COMPLETED')
                        AND t.departure_time>=?
                        AND t.departure_time<?
                  ) < 2
                  AND NOT EXISTS (
                      SELECT 1
                      FROM trip_staff_assignments tsa
                      JOIN trips t ON t.id=tsa.trip_id
                      WHERE tsa.staff_id=u.id
                        AND tsa.status='ACTIVE'
                        AND t.status NOT IN ('CANCELLED','COMPLETED')
                        AND t.departure_time < ?
                        AND t.arrival_time > ?
                  )
                ORDER BY RAND()
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"),
                companyId, companyId,
                dayStart, dayEnd,
                arrivalTime + MIN_STAFF_TRIP_GAP_MS,
                departureTime - MIN_STAFF_TRIP_GAP_MS);
        return values.isEmpty() ? null : values.get(0);
    }

    private Long findDefaultCompanyId() {
        List<Long> values = jdbcTemplate.query("""
                SELECT id
                FROM bus_companies
                WHERE name='BusBooking Express'
                ORDER BY id
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong(1));
        if (!values.isEmpty()) {
            return values.get(0);
        }
        values = jdbcTemplate.query("SELECT id FROM bus_companies ORDER BY id LIMIT 1", (rs, rowNum) -> rs.getLong(1));
        return values.isEmpty() ? null : values.get(0);
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private long dayStart(long epochMillis) {
        return java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(VN_ZONE)
                .toLocalDate()
                .atStartOfDay(VN_ZONE)
                .toInstant()
                .toEpochMilli();
    }

    private record TripTemplate(String originId, String destinationId, String licensePlate, long departMs) {}
    private record TemplateContext(long routeId, long busId, Long companyId, double price, long durationMs) {}
    private record TripRef(Long id, String status) {}
}
