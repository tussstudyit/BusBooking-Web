package com.example.busbooking.user.model;

import java.math.BigDecimal;
import java.util.List;

public final class UserWebModels {
    private UserWebModels() {
    }

    public record UserSession(
            Long id,
            String name,
            String email,
            String phone,
            String role
    ) {
    }

    public record RouteCard(
            Long id,
            String origin,
            String destination,
            Integer distance,
            Integer seatCount,
            BigDecimal suggestedPrice,
            Long nextDepartureTime,
            Integer tripCount,
            List<RouteSeatOption> seatOptions
    ) {
        public RouteCard(
                Long id,
                String origin,
                String destination,
                Integer distance,
                Integer seatCount,
                BigDecimal suggestedPrice,
                Long nextDepartureTime,
                Integer tripCount
        ) {
            this(id, origin, destination, distance, seatCount, suggestedPrice, nextDepartureTime, tripCount,
                    List.of(new RouteSeatOption(seatCount, suggestedPrice, nextDepartureTime, tripCount)));
        }
    }

    public record RouteSeatOption(
            Integer seatCount,
            BigDecimal suggestedPrice,
            Long nextDepartureTime,
            Integer tripCount
    ) {
    }

    public record TripView(
            Long id,
            Long routeId,
            Long busId,
            String origin,
            String destination,
            Integer distance,
            Long departureTime,
            Long arrivalTime,
            Long tripDate,
            BigDecimal price,
            String status,
            String busName,
            String licensePlate,
            Integer totalSeats,
            Integer availableSeats
    ) {
        public String routeLabel() {
            return origin + " -> " + destination;
        }
    }

    public record SeatView(
            Long id,
            Long busId,
            String seatNumber,
            Integer floor,
            Integer rowIndex,
            Integer columnIndex,
            boolean window,
            boolean aisle,
            String seatType,
            boolean booked
    ) {
    }

    public record TicketView(
            Long id,
            Long userId,
            Long tripId,
            Long seatId,
            Long bookingTime,
            String status,
            String cancellationReason,
            BigDecimal refundAmount,
            String refundStatus,
            String paymentId,
            String passengerName,
            String passengerEmail,
            String passengerPhone,
            TripView trip,
            SeatView seat,
            String qrContent,
            String qrImageBase64,
            String qrMimeType
    ) {
        public boolean paid() {
            return "CONFIRMED".equalsIgnoreCase(status) || "CHECKED_IN".equalsIgnoreCase(status);
        }

        public boolean cancellable() {
            return "PENDING".equalsIgnoreCase(status)
                    || "PENDING_PAYMENT".equalsIgnoreCase(status)
                    || "PAYMENT_FAILED".equalsIgnoreCase(status)
                    || "FAILED".equalsIgnoreCase(status)
                    || "EXPIRED".equalsIgnoreCase(status);
        }
    }

    public record PaymentView(
            String id,
            Long userId,
            Long tripId,
            Long seatId,
            BigDecimal amount,
            String provider,
            String status,
            String paymentUrl,
            String qrContent,
            String qrImageBase64,
            String qrMimeType,
            Long createdAt,
            Long updatedAt,
            Long expiresAt,
            List<TicketView> tickets
    ) {
        public boolean cancellable() {
            return !"SUCCESS".equalsIgnoreCase(status) && !"CANCELLED".equalsIgnoreCase(status);
        }
    }

    public record CheckoutResult(
            String paymentId,
            List<Long> ticketIds
    ) {
    }
}
