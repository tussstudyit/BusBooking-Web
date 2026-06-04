package com.example.busbooking.admin.model;

public record SeatDto(
        String documentId,
        Long id,
        Long busId,
        String seatNumber,
        Integer floor,
        Integer rowIndex,
        Integer columnIndex,
        Boolean isWindow,
        Boolean isAisle,
        String seatType,
        Long createdAt
) {
}
