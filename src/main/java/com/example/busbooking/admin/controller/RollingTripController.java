package com.example.busbooking.admin.controller;

import com.example.busbooking.admin.service.RollingTripGeneratorService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RollingTripController {
    private final RollingTripGeneratorService rollingTripGeneratorService;

    public RollingTripController(RollingTripGeneratorService rollingTripGeneratorService) {
        this.rollingTripGeneratorService = rollingTripGeneratorService;
    }

    @PostMapping("/api/rolling-trips/refresh")
    public Map<String, Object> refresh() {
        try {
            return rollingTripGeneratorService.generateUpcomingTrips();
        } catch (IllegalStateException e) {
            return Map.of("code", "99", "message", rootMessage(e));
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.getMessage() : current.getMessage();
    }
}