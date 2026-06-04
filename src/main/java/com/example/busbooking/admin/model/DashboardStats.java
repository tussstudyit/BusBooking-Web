package com.example.busbooking.admin.model;

public record DashboardStats(
        long users,
        long activeRoutes,
        long activeBuses,
        long scheduledTrips,
        long tickets,
        long successfulPayments
) {
}
