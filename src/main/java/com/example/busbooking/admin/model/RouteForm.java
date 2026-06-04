package com.example.busbooking.admin.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class RouteForm {
    @NotBlank
    private String originId;

    @NotBlank
    private String destinationId;

    private String origin;

    private String destination;

    @Min(1)
    private Integer distance;

    @Min(1)
    private Integer seatCount = 34;

    @Min(1)
    private Long suggestedPrice;

    private Boolean isActive = true;

    public String getOriginId() {
        return originId;
    }

    public void setOriginId(String originId) {
        this.originId = originId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(String destinationId) {
        this.destinationId = destinationId;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public Integer getDistance() {
        return distance;
    }

    public void setDistance(Integer distance) {
        this.distance = distance;
    }

    public Integer getSeatCount() {
        return seatCount;
    }

    public void setSeatCount(Integer seatCount) {
        this.seatCount = seatCount;
    }

    public Long getSuggestedPrice() {
        return suggestedPrice;
    }

    public void setSuggestedPrice(Long suggestedPrice) {
        this.suggestedPrice = suggestedPrice;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }
}
