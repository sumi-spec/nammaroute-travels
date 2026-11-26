package com.nammaroute.service;

import com.nammaroute.model.Booking;
import com.nammaroute.model.Bus;
import com.nammaroute.model.Passenger;
import com.nammaroute.model.Seat;
import com.nammaroute.storage.FileStorageService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Service handling ticket reservation, atomic seat assignment, cancellation, and refund calculations.
 */
public class BookingService {
    private final Map<String, Booking> bookings;
    private final BusService busService;
    private final FileStorageService storageService;
    private final Random random;
    private WaitlistService waitlistService;

    public BookingService(BusService busService, FileStorageService storageService) {
        this.busService = busService;
        this.storageService = storageService;
        this.bookings = new LinkedHashMap<>();
        this.random = new Random();
        loadBookings();
    }

    public void setWaitlistService(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    public synchronized void addConfirmedBooking(Booking booking) {
        if (booking != null) {
            bookings.put(booking.getPnr(), booking);
        }
    }

    public void loadBookings() {
        bookings.clear();
        Map<String, Booking> loaded = storageService.loadBookings(busService.getBusMap());
        bookings.putAll(loaded);
    }

    /**
     * Generates a unique PNR in format PNR-XXXXX.
     */
    public synchronized String generateUniquePnr() {
        while (true) {
            int num = 10000 + random.nextInt(90000); // 5 digits: 10000 to 99999
            String pnr = "PNR-" + num;
            if (!bookings.containsKey(pnr)) {
                return pnr;
            }
        }
    }

    /**
     * Atomically books seats for a list of passengers on a given route.
     */
    public synchronized Booking bookTickets(String routeId, List<Passenger> passengerList) {
        Bus bus = busService.getBus(routeId);
        if (bus == null) {
            System.out.println(">> Error: Route ID '" + routeId + "' not found.");
            return null;
        }

        if (passengerList == null || passengerList.isEmpty()) {
            System.out.println(">> Error: No passenger details provided.");
            return null;
        }

        // 1. ATOMIC VALIDATION: Check that ALL requested seats are valid and unbooked
        for (Passenger p : passengerList) {
            String seatNum = p.getSeatNumber();
            Seat seat = bus.getSeat(seatNum);
            if (seat == null) {
                System.out.println(">> Booking Aborted: Seat '" + seatNum + "' is invalid for route " + routeId);
                return null;
            }
            if (seat.isBooked()) {
                System.out.println(">> Booking Aborted: Seat '" + seat.getDisplayLabel() + "' is already booked by another passenger.");
                return null;
            }
        }

        // 2. FEMALE CO-SEAT SAFETY VALIDATION (Version 2.0 Change 2)
        String safetyError = CoSeatSafetyService.validateFemaleCoSeatRule(bus, passengerList, this);
        if (safetyError != null) {
            System.out.println(">> Booking Aborted: " + safetyError);
            return null;
        }

        // 3. ATOMIC ASSIGNMENT: All seats and safety rules verified; book them now
        for (Passenger p : passengerList) {
            bus.bookSeat(p.getSeatNumber());
        }

        // 4. Generate unique PNR and create booking
        String pnr = generateUniquePnr();
        Booking booking = new Booking(pnr, bus.getRouteId(), LocalDateTime.now(), passengerList);
        bookings.put(pnr, booking);

        // 5. Persist immediately
        storageService.saveBookings(bookings.values());

        // 6. Display official receipt
        booking.printReceipt(bus);

        return booking;
    }

    /**
     * Cancels either the whole booking or selected seats from a multi-seat booking.
     */
    public synchronized boolean cancelTickets(String pnr, List<String> seatNumbersToCancel) {
        if (pnr == null || pnr.trim().isEmpty()) {
            System.out.println(">> Error: PNR cannot be empty.");
            return false;
        }

        String normalizedPnr = pnr.trim().toUpperCase();
        Booking booking = bookings.get(normalizedPnr);
        if (booking == null) {
            System.out.println(">> Error: No booking found with PNR '" + normalizedPnr + "'.");
            return false;
        }

        if (!booking.isConfirmed()) {
            System.out.println(">> Error: Booking " + normalizedPnr + " is already fully CANCELLED.");
            return false;
        }

        Bus bus = busService.getBus(booking.getRouteId());
        List<Passenger> activePassengers = booking.getActivePassengers();

        // If seatNumbersToCancel is null/empty or contains "ALL", cancel entire booking
        boolean cancelEntire = (seatNumbersToCancel == null || seatNumbersToCancel.isEmpty());

        List<String> cancelledSeatsList = new ArrayList<>();
        double refundAmount = 0.0;

        if (cancelEntire) {
            int cancelledCount = activePassengers.size();
            double farePerSeat = (cancelledCount > 0) ? activePassengers.get(0).getFare() : (bus != null ? bus.getBasePrice() : 0.0);
            refundAmount = calculateRefund(cancelledCount, farePerSeat);
            booking.cancelAll();

            for (Passenger p : activePassengers) {
                String seatNum = p.getSeatNumber();
                if (bus != null) {
                    bus.releaseSeat(seatNum);
                }
                cancelledSeatsList.add(seatNum);

                // Check FIFO Waitlist for Auto-Promotion (Version 2.0 Change 3)
                if (waitlistService != null && bus != null) {
                    waitlistService.promoteNextEligiblePassenger(bus, seatNum, this);
                }
            }
        } else {
            // Cancel specific seats
            // Validate all requested seats are actually active in this booking
            List<String> validActiveSeatsToCancel = new ArrayList<>();
            for (String seatRaw : seatNumbersToCancel) {
                String normalizedSeat = Seat.normalize(seatRaw);
                Passenger p = booking.getPassengerForSeat(normalizedSeat);
                if (p == null || !p.isConfirmed()) {
                    System.out.println(">> Error: Seat '" + normalizedSeat + "' is not an active booked seat in PNR " + normalizedPnr + ".");
                    return false;
                }
                validActiveSeatsToCancel.add(normalizedSeat);
            }

            // Perform partial cancellation
            for (String seatNum : validActiveSeatsToCancel) {
                Passenger p = booking.getPassengerForSeat(seatNum);
                double fare = p != null ? p.getFare() : (bus != null ? bus.getBasePrice() : 0.0);
                refundAmount += calculateRefund(1, fare);

                if (bus != null) {
                    bus.releaseSeat(seatNum);
                }
                booking.cancelSeat(seatNum);
                cancelledSeatsList.add(seatNum);

                // Check FIFO Waitlist for Auto-Promotion (Version 2.0 Change 3)
                if (waitlistService != null && bus != null) {
                    waitlistService.promoteNextEligiblePassenger(bus, seatNum, this);
                }
            }
        }

        // Persist changes
        storageService.saveBookings(bookings.values());

        // Display cancellation confirmation
        displayCancellationConfirmation(booking, bus, cancelledSeatsList, refundAmount);

        return true;
    }

    /**
     * Isolated refund calculation method.
     * Policy: 100% refund of the cancelled seat fare.
     * Can be easily adjusted in the future for cancellation fee rules.
     */
    public double calculateRefund(int numberOfSeats, double farePerSeat) {
        double refundPercentage = 1.00; // 100% refund as per demonstration policy
        return numberOfSeats * farePerSeat * refundPercentage;
    }

    private void displayCancellationConfirmation(Booking booking, Bus bus, List<String> cancelledSeats, double refund) {
        System.out.println();
        System.out.println("==========================================================================");
        System.out.println("                           NAMMAROUTE TRAVELS                             ");
        System.out.println("                      CANCELLATION & REFUND RECEIPT                       ");
        System.out.println("==========================================================================");
        System.out.printf("PNR Number          : %s%n", booking.getPnr());
        System.out.printf("Route ID            : %s (%s -> %s)%n",
                booking.getRouteId(),
                bus != null ? bus.getOrigin() : "N/A",
                bus != null ? bus.getDestination() : "N/A");
        System.out.printf("Cancelled Seats     : %s%n", cancelledSeats);
        System.out.printf("Refund Rate         : 100%%%n");
        System.out.printf("Total Refund Amount : Rs. %.2f%n", refund);
        System.out.printf("Current Status      : %s%n", booking.getStatus());
        if (booking.isConfirmed()) {
            System.out.printf("Remaining Seats     : %s%n", booking.getActiveSeatNumbers());
            System.out.printf("Remaining Fare      : Rs. %.2f%n", booking.getTotalFare());
        }
        System.out.println("==========================================================================");
        System.out.println("Seats have been released and are now available for other passengers.");
        System.out.println("==========================================================================");
        System.out.println();
    }

    public void searchBookingByPnr(String pnr) {
        if (pnr == null || pnr.trim().isEmpty()) {
            System.out.println(">> Error: PNR cannot be empty.");
            return;
        }

        String normalized = pnr.trim().toUpperCase();
        Booking booking = bookings.get(normalized);
        if (booking == null) {
            System.out.println(">> No booking found for PNR: " + normalized);
            return;
        }

        Bus bus = busService.getBus(booking.getRouteId());
        booking.printReceipt(bus);
    }

    public Collection<Booking> getAllBookings() {
        return bookings.values();
    }

    public Booking getBooking(String pnr) {
        if (pnr == null) return null;
        return bookings.get(pnr.trim().toUpperCase());
    }

    /**
     * Finds the active confirmed passenger currently assigned to a specific seat on a route.
     * Returns null if the seat is unbooked or cancelled.
     */
    public Passenger getActivePassengerOnSeat(String routeId, String seatNumber) {
        if (routeId == null || seatNumber == null) return null;
        String normalizedSeat = Seat.normalize(seatNumber);
        for (Booking booking : bookings.values()) {
            if (booking.isConfirmed() && booking.getRouteId().equalsIgnoreCase(routeId)) {
                Passenger p = booking.getPassengerForSeat(normalizedSeat);
                if (p != null && p.isConfirmed()) {
                    return p;
                }
            }
        }
        return null;
    }
}
