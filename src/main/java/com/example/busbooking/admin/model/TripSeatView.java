package com.example.busbooking.admin.model;

public record TripSeatView(
        Long seatId,
        String seatNumber,
        Integer floor,
        Integer rowIndex,
        Integer columnIndex,
        String status
) {
    public boolean isAvailable() {
        return "AVAILABLE".equals(status);
    }

    public boolean isBooked() {
        return "BOOKED".equals(status);
    }

    public String cssClass() {
        return status == null ? "available" : status.toLowerCase();
    }

    public int gridColumn() {
        return columnIndex == null ? 1 : columnIndex + 1;
    }
}
