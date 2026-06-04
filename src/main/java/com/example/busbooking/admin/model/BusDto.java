package com.example.busbooking.admin.model;

public record BusDto(
        String documentId,
        Long id,
        String busName,
        Integer totalSeats,
        String licensePlate,
        String serviceProvinceId,
        String seatLayoutJson,
        Boolean isActive,
        Long createdAt
) {
}
