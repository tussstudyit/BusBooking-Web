package com.example.busbooking.admin.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class TripForm {
    @NotNull
    private Long routeId;

    @NotNull
    private Long busId;

    @NotNull
    private Long departureTime;

    @NotNull
    private Long arrivalTime;

    @Min(1)
    private Double price;

    @NotNull
    private Long tripDate;

    private String status = "SCHEDULED";

    private Long staffId;

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId(Long routeId) {
        this.routeId = routeId;
    }

    public Long getBusId() {
        return busId;
    }

    public void setBusId(Long busId) {
        this.busId = busId;
    }

    public Long getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(Long departureTime) {
        this.departureTime = departureTime;
    }

    public Long getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(Long arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Long getTripDate() {
        return tripDate;
    }

    public void setTripDate(Long tripDate) {
        this.tripDate = tripDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getStaffId() {
        return staffId;
    }

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }
}
