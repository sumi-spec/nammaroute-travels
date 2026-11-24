package com.nammaroute.service;

import com.nammaroute.model.Booking;
import com.nammaroute.model.Bus;
import com.nammaroute.model.Passenger;
import com.nammaroute.model.Seat;

import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for generating sorted passenger manifests for specific bus routes.
 */
public class ManifestService {

    public static class ManifestEntry {
        private final String seatNumber;
        private final String pnr;
        private final String passengerName;
        private final int age;
        private final String gender;

        public ManifestEntry(String seatNumber, String pnr, String passengerName, int age, String gender) {
            this.seatNumber = seatNumber;
            this.pnr = pnr;
            this.passengerName = passengerName;
            this.age = age;
            this.gender = gender;
        }

        public String getSeatNumber() {
            return seatNumber;
        }

        public String getPnr() {
            return pnr;
        }

        public String getPassengerName() {
            return passengerName;
        }

        public int getAge() {
            return age;
        }

        public String getGender() {
            return gender;
        }
    }

    /**
     * Generates and prints the passenger manifest for a given Route ID.
     */
    public void generateManifest(String routeId, BusService busService, BookingService bookingService) {
        if (routeId == null || routeId.trim().isEmpty()) {
            System.out.println(">> Error: Route ID cannot be empty.");
            return;
        }

        String normalizedRoute = routeId.trim().toUpperCase();
        Bus bus = busService.getBus(normalizedRoute);
        if (bus == null) {
            System.out.println(">> Error: Bus route '" + normalizedRoute + "' not found.");
            return;
        }

        // Collect all active passengers on this route
        List<ManifestEntry> entries = new ArrayList<>();
        for (Booking booking : bookingService.getAllBookings()) {
            if (booking.getRouteId().equalsIgnoreCase(normalizedRoute) && booking.isConfirmed()) {
                for (Passenger passenger : booking.getActivePassengers()) {
                    entries.add(new ManifestEntry(
                            passenger.getSeatNumber(),
                            booking.getPnr(),
                            passenger.getName(),
                            passenger.getAge(),
                            passenger.getGender()
                    ));
                }
            }
        }

        // Sort passengers naturally by seat number
        entries.sort((e1, e2) -> Seat.compareSeats(e1.getSeatNumber(), e2.getSeatNumber()));

        // Display the manifest
        System.out.println();
        System.out.println("==========================================================================================================");
        System.out.println("                                       OFFICIAL PASSENGER MANIFEST                                        ");
        System.out.println("==========================================================================================================");
        System.out.printf("Route ID       : %s%n", bus.getRouteId());
        System.out.printf("Journey        : %s to %s%n", bus.getOrigin(), bus.getDestination());
        System.out.printf("Departure Time : %s%n", bus.getDepartureTime().format(Bus.DATE_TIME_FORMATTER));
        System.out.printf("Bus Type       : %s (%d Seats)%n", bus.getBusType().getDisplayName(), bus.getTotalSeats());
        System.out.printf("Base Ticket Fare: Rs. %.2f%n", bus.getBasePrice());
        System.out.println("----------------------------------------------------------------------------------------------------------");
        System.out.printf("%-10s %-14s %-32s %-8s %-10s%n",
                "Seat No.", "PNR", "Passenger Name", "Age", "Gender");
        System.out.println("----------------------------------------------------------------------------------------------------------");

        if (entries.isEmpty()) {
            System.out.println(">> No passengers currently booked on this route.");
        } else {
            for (ManifestEntry entry : entries) {
                Seat seat = bus.getSeat(entry.getSeatNumber());
                String displaySeat = seat != null ? seat.getDisplayLabel() : entry.getSeatNumber();
                System.out.printf("%-10s %-14s %-32s %-8d %-10s%n",
                        displaySeat,
                        entry.getPnr(),
                        entry.getPassengerName(),
                        entry.getAge(),
                        entry.getGender());
            }
        }

        System.out.println("----------------------------------------------------------------------------------------------------------");
        System.out.println("MANIFEST SUMMARY:");
        System.out.printf("Total Bus Capacity : %d%n", bus.getTotalSeats());
        System.out.printf("Total Booked Seats : %d%n", bus.getBookedSeatsCount());
        System.out.printf("Total Available    : %d%n", bus.getAvailableSeatsCount());
        System.out.printf("Total Passengers   : %d%n", entries.size());
        System.out.println("==========================================================================================================");
        System.out.println();
    }
}
