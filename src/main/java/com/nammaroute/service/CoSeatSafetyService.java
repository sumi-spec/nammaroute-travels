package com.nammaroute.service;

import com.nammaroute.model.Bus;
import com.nammaroute.model.BusType;
import com.nammaroute.model.Passenger;
import com.nammaroute.model.Seat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service enforcing the Female Passenger Co-Seat Safety Rule (Change 2)
 * and providing compatibility checks for Waitlist auto-promotions (Change 3).
 */
public class CoSeatSafetyService {

    /**
     * Determines the adjacent paired seat for a given seat number based on bus configuration.
     * - 2x2 Seater: A <-> B (window/aisle left), C <-> D (aisle/window right).
     * - 2x1 Sleeper: L1 <-> L2, L4 <-> L5, etc. L3, L6, etc. are single berths across aisle.
     * Returns null if the seat has no adjacent paired berth.
     */
    public static String getAdjacentSeat(BusType busType, String seatNumber) {
        if (seatNumber == null || seatNumber.trim().isEmpty()) return null;
        String normalized = Seat.normalize(seatNumber);

        if (busType == BusType.SEATER) {
            if (normalized.matches("^[1-8][A-D]$")) {
                char row = normalized.charAt(0);
                char col = normalized.charAt(1);
                switch (col) {
                    case 'A': return row + "B";
                    case 'B': return row + "A";
                    case 'C': return row + "D";
                    case 'D': return row + "C";
                }
            }
        } else if (busType == BusType.SLEEPER) {
            if (normalized.matches("^[LU]([1-9]|1[0-2])$")) {
                char deck = normalized.charAt(0);
                int berthNum = Integer.parseInt(normalized.substring(1));
                int mod = berthNum % 3;
                if (mod == 1) {
                    return deck + String.valueOf(berthNum + 1); // e.g. L1 <-> L2, U4 <-> U5
                } else if (mod == 2) {
                    return deck + String.valueOf(berthNum - 1); // e.g. L2 <-> L1, U5 <-> U4
                } else {
                    return null; // Single berth across aisle (L3, L6, L9, L12, U3, U6, etc.)
                }
            }
        }
        return null;
    }

    /**
     * Validates the Female Passenger Co-Seat Safety Rule:
     * - In a 2-seat pair, if one seat is booked by a Female passenger, the seat right next to her
     *   can only be booked by another Female passenger.
     * - If a Male passenger tries to book, returns the exact error:
     *   "Sorry, seat <SEAT> is reserved for a female co-passenger. Please choose another seat."
     * - Group / Family Booking Exception: If two passengers book both seats together on the same PNR,
     *   allow the booking!
     *
     * Returns null if valid, or the error message string if blocked.
     */
    public static String validateFemaleCoSeatRule(Bus bus, List<Passenger> requestedPassengers, BookingService bookingService) {
        if (bus == null || requestedPassengers == null || requestedPassengers.isEmpty()) {
            return null;
        }

        // Map of seat numbers included in THIS batch booking
        Map<String, Passenger> currentBatchSeats = new HashMap<>();
        for (Passenger p : requestedPassengers) {
            currentBatchSeats.put(Seat.normalize(p.getSeatNumber()), p);
        }

        for (Passenger p : requestedPassengers) {
            String seatNum = Seat.normalize(p.getSeatNumber());
            String adjacentSeat = getAdjacentSeat(bus.getBusType(), seatNum);

            if (adjacentSeat == null) {
                // Single berth with no adjacent neighbor
                continue;
            }

            // GROUP / FAMILY EXCEPTION:
            // If both adjacent seats are in this exact same booking transaction, permit it.
            if (currentBatchSeats.containsKey(adjacentSeat)) {
                continue;
            }

            // Check if adjacent seat is already booked by an existing confirmed passenger
            Passenger existingNeighbor = bookingService.getActivePassengerOnSeat(bus.getRouteId(), adjacentSeat);
            if (existingNeighbor != null && existingNeighbor.isConfirmed()) {
                boolean isNeighborFemale = isFemale(existingNeighbor.getGender());
                if (isNeighborFemale) {
                    boolean isNewPassengerFemale = isFemale(p.getGender());
                    if (!isNewPassengerFemale) {
                        Seat seatObj = bus.getSeat(seatNum);
                        String displayLabel = seatObj != null ? seatObj.getDisplayLabel() : seatNum;
                        return String.format("Sorry, seat %s is reserved for a female co-passenger. Please choose another seat.", displayLabel);
                    }
                }
            }
        }

        return null; // Passes all safety checks
    }

    /**
     * Checks if a candidate passenger (by gender) is allowed to take a specific seat on a bus.
     * Used by Waitlist Auto-Promotion smart safety check.
     */
    public static boolean isSeatSafeForPassenger(Bus bus, String seatNumber, String passengerGender, BookingService bookingService) {
        if (bus == null || seatNumber == null) return true;
        String adjacentSeat = getAdjacentSeat(bus.getBusType(), seatNumber);
        if (adjacentSeat == null) return true;

        Passenger existingNeighbor = bookingService.getActivePassengerOnSeat(bus.getRouteId(), adjacentSeat);
        if (existingNeighbor != null && existingNeighbor.isConfirmed()) {
            boolean isNeighborFemale = isFemale(existingNeighbor.getGender());
            if (isNeighborFemale) {
                return isFemale(passengerGender);
            }
        }
        return true;
    }

    private static boolean isFemale(String gender) {
        if (gender == null) return false;
        String g = gender.trim().toUpperCase();
        return g.equals("F") || g.equals("FEMALE");
    }
}
