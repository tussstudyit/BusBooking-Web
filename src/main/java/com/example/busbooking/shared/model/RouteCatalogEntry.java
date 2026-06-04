package com.example.busbooking.shared.model;

public record RouteCatalogEntry(
        String originId,
        String originName,
        String destinationId,
        String destinationName,
        int distanceKm,
        long durationMs,
        long price
) {
}
