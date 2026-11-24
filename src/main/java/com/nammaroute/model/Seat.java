package com.nammaroute.model;

import java.util.Objects;

/**
 * Represents a single seat or berth on a bus.
 */
public class Seat {
    private final String seatNumber; // Normalized seat number e.g. "1A", "L1", "U1"
    private final String seatType;   // "WINDOW", "AISLE", "LOWER_BERTH", "UPPER_BERTH"
    private boolean isBooked;

    public Seat(String seatNumber, String seatType) {
        this.seatNumber = normalize(seatNumber);
        this.seatType = seatType;
        this.isBooked = false;
    }

    /**
     * Normalizes seat numbers:
     * - "01A" or "1a" -> "1A"
     * - "L01" or "l1" -> "L1"
     * - "U01" or "u1" -> "U1"
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim().toUpperCase();
        if (trimmed.matches("^0*([1-8])([A-D])$")) {
            return trimmed.replaceFirst("^0*([1-8])([A-D])$", "$1$2");
        }
        if (trimmed.matches("^L0*([1-9]|1[0-2])$")) {
            return trimmed.replaceFirst("^L0*([1-9]|1[0-2])$", "L$1");
        }
        if (trimmed.matches("^U0*([1-9]|1[0-2])$")) {
            return trimmed.replaceFirst("^U0*([1-9]|1[0-2])$", "U$1");
        }
        return trimmed;
    }

    /**
     * Returns a padded 3-character display label:
     * e.g. "1A" -> "01A", "L1" -> "L01", "U12" -> "U12"
     */
    public String getDisplayLabel() {
        if (seatNumber.matches("^([1-8])([A-D])$")) {
            int row = Integer.parseInt(seatNumber.substring(0, 1));
            char col = seatNumber.charAt(1);
            return String.format("%02d%c", row, col);
        } else if (seatNumber.matches("^([LU])([1-9]|1[0-2])$")) {
            char deck = seatNumber.charAt(0);
            int num = Integer.parseInt(seatNumber.substring(1));
            return String.format("%c%02d", deck, num);
        }
        return seatNumber;
    }

    /**
     * Natural comparator for seat ordering.
     */
    public static int compareSeats(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return -1;
        if (s2 == null) return 1;
        String n1 = normalize(s1);
        String n2 = normalize(s2);

        boolean s1Seater = n1.matches("^([1-8])([A-D])$");
        boolean s2Seater = n2.matches("^([1-8])([A-D])$");
        if (s1Seater && s2Seater) {
            int r1 = Integer.parseInt(n1.substring(0, 1));
            int r2 = Integer.parseInt(n2.substring(0, 1));
            if (r1 != r2) return Integer.compare(r1, r2);
            return Character.compare(n1.charAt(1), n2.charAt(1));
        }

        boolean s1Sleeper = n1.matches("^([LU])([1-9]|1[0-2])$");
        boolean s2Sleeper = n2.matches("^([LU])([1-9]|1[0-2])$");
        if (s1Sleeper && s2Sleeper) {
            char d1 = n1.charAt(0);
            char d2 = n2.charAt(0);
            if (d1 != d2) {
                // Lower deck 'L' before Upper deck 'U'
                return Character.compare(d1, d2);
            }
            int num1 = Integer.parseInt(n1.substring(1));
            int num2 = Integer.parseInt(n2.substring(1));
            return Integer.compare(num1, num2);
        }

        return n1.compareTo(n2);
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public String getSeatType() {
        return seatType;
    }

    public boolean isBooked() {
        return isBooked;
    }

    public void setBooked(boolean booked) {
        isBooked = booked;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Seat seat = (Seat) o;
        return Objects.equals(seatNumber, seat.seatNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(seatNumber);
    }

    @Override
    public String toString() {
        return getDisplayLabel() + (isBooked ? " [XX]" : " [Available]");
    }
}
