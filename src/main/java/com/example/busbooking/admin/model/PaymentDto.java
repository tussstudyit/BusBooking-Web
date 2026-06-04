package com.example.busbooking.admin.model;

public record PaymentDto(
        String documentId,
        String ticketId,
        String userId,
        Long tripId,
        Long seatId,
        Double amount,
        String provider,
        String status,
        String vnpTxnRef,
        String vnpTransactionNo,
        Long createdAt,
        Long updatedAt
) {
}
