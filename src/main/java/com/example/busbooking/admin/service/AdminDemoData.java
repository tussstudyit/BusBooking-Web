package com.example.busbooking.admin.service;

import com.example.busbooking.admin.model.BusDto;
import com.example.busbooking.admin.model.RouteDto;
import com.example.busbooking.admin.model.TripDto;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class AdminDemoData {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long HOUR_MS = 3_600_000L;

    private AdminDemoData() {
    }

    public static List<RouteDto> routes() {
        return List.of(
                route(1L, "ha_noi", "Hà Nội", "da_nang", "Đà Nẵng", 765, 650_000L, 13),
                route(2L, "da_nang", "Đà Nẵng", "ha_noi", "Hà Nội", 765, 650_000L, 13),
                route(3L, "da_nang", "Đà Nẵng", "ho_chi_minh", "TP. Hồ Chí Minh", 980, 850_000L, 16),
                route(4L, "ho_chi_minh", "TP. Hồ Chí Minh", "da_nang", "Đà Nẵng", 980, 850_000L, 16)
        );
    }

    public static List<BusDto> buses() {
        long now = System.currentTimeMillis();
        return List.of(
                new BusDto("demo-bus-1", 1L, "Da Nang Express 34", 34, "43A-12345", "da_nang", "", true, now),
                new BusDto("demo-bus-2", 2L, "Da Nang Express 34", 34, "43A-23456", "da_nang", "", true, now)
        );
    }

    public static List<TripDto> trips() {
        long dayStart = LocalDate.now(VN_ZONE).atStartOfDay(VN_ZONE).toInstant().toEpochMilli();
        return List.of(
                trip(1L, 1L, 1L, "Hà Nội -> Đà Nẵng", "43A-12345", dayStart + 7 * HOUR_MS, 13, 650_000.0),
                trip(2L, 2L, 2L, "Đà Nẵng -> Hà Nội", "43A-23456", dayStart + 15 * HOUR_MS, 13, 650_000.0),
                trip(3L, 3L, 1L, "Đà Nẵng -> TP. Hồ Chí Minh", "43A-12345", dayStart + 20 * HOUR_MS, 16, 850_000.0),
                trip(4L, 4L, 2L, "TP. Hồ Chí Minh -> Đà Nẵng", "43A-23456", dayStart + 22 * HOUR_MS, 16, 850_000.0)
        );
    }

    private static RouteDto route(
            Long id,
            String originId,
            String origin,
            String destinationId,
            String destination,
            Integer distance,
            Long price,
            long durationHours
    ) {
        return new RouteDto(
                "demo-route-" + id,
                id,
                originId,
                destinationId,
                origin,
                destination,
                distance,
                34,
                price,
                durationHours * HOUR_MS,
                true,
                System.currentTimeMillis()
        );
    }

    private static TripDto trip(
            Long id,
            Long routeId,
            Long busId,
            String routeLabel,
            String busLicensePlate,
            long departureTime,
            long durationHours,
            Double price
    ) {
        return new TripDto(
                "demo-trip-" + id,
                id,
                routeId,
                busId,
                routeLabel,
                busLicensePlate,
                "Nhan vien demo",
                departureTime,
                departureTime + durationHours * HOUR_MS,
                price,
                LocalDate.now(VN_ZONE).atStartOfDay(VN_ZONE).toInstant().toEpochMilli(),
                "SCHEDULED",
                "SCHEDULED",
                34,
                34,
                System.currentTimeMillis()
        );
    }
}
