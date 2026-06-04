package com.example.busbooking.user.service;

import com.example.busbooking.user.model.UserWebModels.CheckoutResult;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserBookingService {
    private final JdbcTemplate jdbcTemplate;

    public UserBookingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public CheckoutResult bookSeats(long userId, long tripId, List<Long> seatIds) {
        return bookSegments(userId, List.of(new BookingSegment(tripId, seatIds, null)));
    }

    @Transactional
    public CheckoutResult bookSegments(long userId, List<BookingSegment> segments) {
        if (userId <= 0) {
            throw new IllegalArgumentException("Nguoi dung khong hop le");
        }
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("Vui long chon ghe");
        }

        long now = System.currentTimeMillis();
        String paymentId = "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase(Locale.ROOT);
        List<Long> ticketIds = new ArrayList<>();
        List<PaymentItem> paymentItems = new ArrayList<>();
        Long firstTripId = null;
        Long firstSeatId = null;
        BigDecimal totalAmount = BigDecimal.ZERO;

        try {
            for (BookingSegment segment : segments) {
                if (segment == null || segment.tripId() == null || segment.seatIds() == null || segment.seatIds().isEmpty()) {
                    throw new IllegalArgumentException("Vui long chon ghe");
                }

                TripBookingData trip = tripBookingData(segment.tripId());
                BigDecimal seatAmount = trip.price() == null
                        ? (segment.fallbackPrice() == null ? BigDecimal.ZERO : segment.fallbackPrice())
                        : trip.price();

                for (Long seatId : segment.seatIds()) {
                    if (seatId == null) {
                        throw new IllegalArgumentException("Ghe khong hop le");
                    }
                    assertSeatBelongsToTrip(segment.tripId(), seatId);
                    assertSeatAvailable(segment.tripId(), seatId);
                    Long ticketId = insertTicket(userId, segment.tripId(), seatId, trip.busId(), paymentId, now);
                    ticketIds.add(ticketId);
                    paymentItems.add(new PaymentItem(ticketId, seatAmount));
                    totalAmount = totalAmount.add(seatAmount);
                    if (firstTripId == null) {
                        firstTripId = segment.tripId();
                        firstSeatId = seatId;
                    }
                }
            }

            jdbcTemplate.update(
                    "INSERT INTO payments(id,ticket_id,user_id,trip_id,seat_id,amount,provider,status,created_at) VALUES (?,?,?,?,?,?,'VNPAY','CREATED',?)",
                    paymentId,
                    ticketIds.isEmpty() ? null : ticketIds.get(0),
                    userId,
                    firstTripId,
                    firstSeatId,
                    totalAmount,
                    now
            );
            for (PaymentItem item : paymentItems) {
                jdbcTemplate.update(
                        "INSERT INTO payment_items(payment_id,ticket_id,amount,created_at) VALUES (?,?,?,?)",
                        paymentId,
                        item.ticketId(),
                        item.amount(),
                        now
                );
            }
            return new CheckoutResult(paymentId, ticketIds);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("Ghe da duoc dat", e);
        }
    }

    private TripBookingData tripBookingData(long tripId) {
        List<TripBookingData> trips = jdbcTemplate.query(
                "SELECT bus_id, price FROM trips WHERE id=?",
                (rs, rowNum) -> new TripBookingData(rs.getLong("bus_id"), rs.getBigDecimal("price")),
                tripId
        );
        if (trips.isEmpty()) {
            throw new IllegalArgumentException("Khong tim thay chuyen xe");
        }
        return trips.get(0);
    }

    private void assertSeatBelongsToTrip(long tripId, long seatId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM trips t
                JOIN seats s ON s.bus_id=t.bus_id
                WHERE t.id=? AND s.id=?
                """, Integer.class, tripId, seatId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Ghe khong thuoc chuyen xe");
        }
    }

    private void assertSeatAvailable(long tripId, long seatId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT (
                    SELECT COUNT(*)
                    FROM tickets tk
                    WHERE tk.trip_id=?
                      AND tk.seat_id=?
                      AND tk.status IN ('PENDING_PAYMENT','PENDING','CONFIRMED','CHECKED_IN')
                ) + (
                    SELECT COUNT(*)
                    FROM trip_seats ts
                    WHERE ts.trip_id=?
                      AND ts.seat_id=?
                      AND ts.status IN ('BOOKED','CONFIRMED','CHECKED_IN')
                )
                """, Integer.class, tripId, seatId, tripId, seatId);
        if (count != null && count > 0) {
            throw new IllegalStateException("Ghe da duoc dat");
        }
    }

    private Long insertTicket(long userId, long tripId, long seatId, Long busId, String paymentId, long now) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO tickets(user_id,trip_id,seat_id,bus_id,payment_id,booking_time,status,refund_status) VALUES (?,?,?,?,?,?,'PENDING_PAYMENT','NONE')",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, userId);
            statement.setLong(2, tripId);
            statement.setLong(3, seatId);
            if (busId == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, busId);
            }
            statement.setString(5, paymentId);
            statement.setLong(6, now);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Khong tao duoc ve");
        }
        return key.longValue();
    }

    public record BookingSegment(Long tripId, List<Long> seatIds, BigDecimal fallbackPrice) {
    }

    private record TripBookingData(Long busId, BigDecimal price) {
    }

    private record PaymentItem(Long ticketId, BigDecimal amount) {
    }
}
