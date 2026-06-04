package com.example.busbooking.admin.model;

public record TicketDto(
        String documentId,
        Long id,
        String userId,
        Long tripId,
        Long seatId,
        Long busId,
        String paymentId,
        Long bookingTime,
        String status,
        String refundStatus
) {
}
