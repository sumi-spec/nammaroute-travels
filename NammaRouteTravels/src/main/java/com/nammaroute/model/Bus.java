package com.nammaroute.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a Bus service running on a specific route with designated seat layouts.
 */
public class Bus {
    private final String routeId;
    private final String origin;
    private final String destination;
    private final LocalDateTime departureTime;
    private final double basePrice;
    private final BusType busType;
    private final Map<String, Seat> seats;

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public Bus(String routeId, String origin, String destination, LocalDateTime departureTime, double basePrice, BusType busType) {
        this.routeId = routeId != null ? routeId.trim().toUpperCase() : "";
        this.origin = origin != null ? origin.trim() : "";
        this.destination = destination != null ? destination.trim() : "";
        this.departureTime = departureTime;
        this.basePrice = basePrice;
        this.busType = busType != null ? busType : BusType.SEATER;
        this.seats = new LinkedHashMap<>();
        initializeSeats();
    }

    /**
     * Initializes seats depending on bus configuration:
     * - 2x2 Seater: 32 seats (8 rows, 4 seats: A, B, C, D)
     * - 2x1 Sleeper: 24 berths (12 lower berths L1-L12, 12 upper berths U1-U12)
     */
    private void initializeSeats() {
        seats.clear();
        if (busType == BusType.SEATER) {
            for (int row = 1; row <= 8; row++) {
                String seatA = row + "A";
                String seatB = row + "B";
                String seatC = row + "C";
                String seatD = row + "D";
                seats.put(seatA, new Seat(seatA, "WINDOW"));
                seats.put(seatB, new Seat(seatB, "AISLE"));
                seats.put(seatC, new Seat(seatC, "AISLE"));
                seats.put(seatD, new Seat(seatD, "WINDOW"));
            }
        } else if (busType == BusType.SLEEPER) {
            // Lower deck berths L1 - L12
            for (int i = 1; i <= 12; i++) {
                String berth = "L" + i;
                seats.put(berth, new Seat(berth, "LOWER_BERTH"));
            }
            // Upper deck berths U1 - U12
            for (int i = 1; i <= 12; i++) {
                String berth = "U" + i;
                seats.put(berth, new Seat(berth, "UPPER_BERTH"));
            }
        }
    }

    public Seat getSeat(String seatNumber) {
        if (seatNumber == null) return null;
        String normalized = Seat.normalize(seatNumber);
        return seats.get(normalized);
    }

    public boolean isSeatAvailable(String seatNumber) {
        Seat seat = getSeat(seatNumber);
        return seat != null && !seat.isBooked();
    }

    public boolean bookSeat(String seatNumber) {
        Seat seat = getSeat(seatNumber);
        if (seat != null && !seat.isBooked()) {
            seat.setBooked(true);
            return true;
        }
        return false;
    }

    public boolean releaseSeat(String seatNumber) {
        Seat seat = getSeat(seatNumber);
        if (seat != null && seat.isBooked()) {
            seat.setBooked(false);
            return true;
        }
        return false;
    }

    public int getTotalSeats() {
        return seats.size();
    }

    public int getBookedSeatsCount() {
        int count = 0;
        for (Seat seat : seats.values()) {
            if (seat.isBooked()) count++;
        }
        return count;
    }

    public int getAvailableSeatsCount() {
        return getTotalSeats() - getBookedSeatsCount();
    }

    public Map<String, Seat> getSeats() {
        return Collections.unmodifiableMap(seats);
    }

    /**
     * Renders an interactive visual seat map in the console.
     */
    public void displaySeatMap() {
        System.out.println();
        System.out.println("==========================================================================");
        System.out.printf("           VISUAL SEAT MAP - ROUTE: %s (%s -> %s)%n", routeId, origin, destination);
        System.out.println("==========================================================================");
        System.out.printf("Bus Type    : %s (%d Seats)%n", busType.getDisplayName(), getTotalSeats());
        System.out.printf("Departure   : %s%n", departureTime.format(DATE_TIME_FORMATTER));
        System.out.printf("Ticket Fare : Rs. %.2f per seat%n", basePrice);
        System.out.printf("Capacity    : Total: %d  |  Booked: %d  |  Available: %d%n",
                getTotalSeats(), getBookedSeatsCount(), getAvailableSeatsCount());
        System.out.println("--------------------------------------------------------------------------");

        if (busType == BusType.SEATER) {
            displaySeaterMap();
        } else {
            displaySleeperMap();
        }

        System.out.println("--------------------------------------------------------------------------");
        System.out.println("Legend: [01A] / [L01] = Available     [ XX] = Booked");
        System.out.println("==========================================================================");
        System.out.println();
    }

    private void displaySeaterMap() {
        System.out.println("                     [ FRONT / DRIVER CABIN ]");
        System.out.println("          LEFT (WINDOW / AISLE)          RIGHT (AISLE / WINDOW)");
        System.out.println("--------------------------------------------------------------------------");
        for (int row = 1; row <= 8; row++) {
            Seat sA = seats.get(row + "A");
            Seat sB = seats.get(row + "B");
            Seat sC = seats.get(row + "C");
            Seat sD = seats.get(row + "D");

            String cellA = sA.isBooked() ? "[ XX]" : "[" + sA.getDisplayLabel() + "]";
            String cellB = sB.isBooked() ? "[ XX]" : "[" + sB.getDisplayLabel() + "]";
            String cellC = sC.isBooked() ? "[ XX]" : "[" + sC.getDisplayLabel() + "]";
            String cellD = sD.isBooked() ? "[ XX]" : "[" + sD.getDisplayLabel() + "]";

            System.out.printf("Row %02d:     %s %s        | AISLE |        %s %s%n",
                    row, cellA, cellB, cellC, cellD);
        }
    }

    private void displaySleeperMap() {
        System.out.println(">>> LOWER DECK (12 Berths - 2x1 Configuration)");
        System.out.println("Row 1:      " + formatSleeperRow("L", 1, 2, 3));
        System.out.println("Row 2:      " + formatSleeperRow("L", 4, 5, 6));
        System.out.println("Row 3:      " + formatSleeperRow("L", 7, 8, 9));
        System.out.println("Row 4:      " + formatSleeperRow("L", 10, 11, 12));
        System.out.println();
        System.out.println(">>> UPPER DECK (12 Berths - 2x1 Configuration)");
        System.out.println("Row 1:      " + formatSleeperRow("U", 1, 2, 3));
        System.out.println("Row 2:      " + formatSleeperRow("U", 4, 5, 6));
        System.out.println("Row 3:      " + formatSleeperRow("U", 7, 8, 9));
        System.out.println("Row 4:      " + formatSleeperRow("U", 10, 11, 12));
    }

    private String formatSleeperRow(String deck, int b1, int b2, int b3) {
        Seat s1 = seats.get(deck + b1);
        Seat s2 = seats.get(deck + b2);
        Seat s3 = seats.get(deck + b3);

        String cell1 = s1.isBooked() ? "[ XX]" : "[" + s1.getDisplayLabel() + "]";
        String cell2 = s2.isBooked() ? "[ XX]" : "[" + s2.getDisplayLabel() + "]";
        String cell3 = s3.isBooked() ? "[ XX]" : "[" + s3.getDisplayLabel() + "]";

        return String.format("%s %s        | AISLE |        %s", cell1, cell2, cell3);
    }

    public String getRouteId() {
        return routeId;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public LocalDateTime getDepartureTime() {
        return departureTime;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public BusType getBusType() {
        return busType;
    }

    @Override
    public String toString() {
        return String.format("%s | %s -> %s | %s | Rs. %.2f | %s | %d/%d seats available",
                routeId, origin, destination, departureTime.format(DATE_TIME_FORMATTER),
                basePrice, busType.getDisplayName(), getAvailableSeatsCount(), getTotalSeats());
    }
}
