package com.nammaroute.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a passenger on the FIFO Waitlist for a fully booked bus route.
 * Version 2.0 Feature: Change 3.
 */
public class WaitlistEntry {
    private final String waitlistId;
    private final String routeId;
    private final String passengerName;
    private final int passengerAge;
    private final String passengerGender;
    private final LocalDateTime requestTime;

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public WaitlistEntry(String waitlistId, String routeId, String passengerName, int passengerAge, String passengerGender, LocalDateTime requestTime) {
        this.waitlistId = waitlistId != null ? waitlistId.trim().toUpperCase() : "";
        this.routeId = routeId != null ? routeId.trim().toUpperCase() : "";
        this.passengerName = passengerName != null ? passengerName.trim() : "";
        this.passengerAge = passengerAge;
        this.passengerGender = passengerGender != null ? passengerGender.trim().toUpperCase() : "M";
        this.requestTime = requestTime != null ? requestTime : LocalDateTime.now();
    }

    public String getWaitlistId() {
        return waitlistId;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public int getPassengerAge() {
        return passengerAge;
    }

    public String getPassengerGender() {
        return passengerGender;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%d, %s) - Requested: %s",
                waitlistId, passengerName, passengerAge, passengerGender, requestTime.format(FORMATTER));
    }
}
