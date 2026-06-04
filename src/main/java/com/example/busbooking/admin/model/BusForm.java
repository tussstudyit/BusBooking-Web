package com.example.busbooking.admin.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class BusForm {
    @NotBlank
    private String busName;

    @Min(24)
    @Max(34)
    private Integer totalSeats = 34;

    @NotBlank
    private String licensePlate;

    private Boolean isActive = true;

    public String getBusName() {
        return busName;
    }

    public void setBusName(String busName) {
        this.busName = busName;
    }

    public Integer getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(Integer totalSeats) {
        this.totalSeats = totalSeats;
    }

    @AssertTrue(message = "Chi ho tro xe 24 hoac 34 ghe")
    public boolean isSupportedSeatLayout() {
        return totalSeats != null && (totalSeats == 24 || totalSeats == 34);
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }
}
