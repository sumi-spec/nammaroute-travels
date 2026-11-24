package com.nammaroute.model;

/**
 * Enumeration representing supported bus types and their capacities.
 */
public enum BusType {
    SEATER("2x2 Seater", 32),
    SLEEPER("2x1 Sleeper", 24);

    private final String displayName;
    private final int totalSeats;

    BusType(String displayName, int totalSeats) {
        this.displayName = displayName;
        this.totalSeats = totalSeats;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public static BusType fromString(String text) {
        if (text == null) return null;
        String normalized = text.trim().toUpperCase();
        if (normalized.contains("SEAT") || normalized.equals("1")) {
            return SEATER;
        } else if (normalized.contains("SLEEP") || normalized.equals("2")) {
            return SLEEPER;
        }
        return null;
    }
}
