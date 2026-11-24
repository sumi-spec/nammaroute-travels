package com.nammaroute.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a ticket booking containing PNR, passenger roster, and payment details.
 */
public class Booking {
    private final String pnr;
    private final String routeId;
    private final LocalDateTime bookingTime;
    private final List<Passenger> passengers;
    private double totalFare;
    private String status; // "CONFIRMED" or "CANCELLED"

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public Booking(String pnr, String routeId, LocalDateTime bookingTime, List<Passenger> passengers) {
        this.pnr = pnr != null ? pnr.trim().toUpperCase() : "";
        this.routeId = routeId != null ? routeId.trim().toUpperCase() : "";
        this.bookingTime = bookingTime != null ? bookingTime : LocalDateTime.now();
        this.passengers = passengers != null ? new ArrayList<>(passengers) : new ArrayList<>();
        this.status = "CONFIRMED";
        recalculateTotalFare();
    }

    public Booking(String pnr, String routeId, LocalDateTime bookingTime, List<Passenger> passengers, double totalFare, String status) {
        this.pnr = pnr != null ? pnr.trim().toUpperCase() : "";
        this.routeId = routeId != null ? routeId.trim().toUpperCase() : "";
        this.bookingTime = bookingTime != null ? bookingTime : LocalDateTime.now();
        this.passengers = passengers != null ? new ArrayList<>(passengers) : new ArrayList<>();
        this.totalFare = totalFare;
        this.status = status != null ? status.trim().toUpperCase() : "CONFIRMED";
    }

    public void recalculateTotalFare() {
        double sum = 0.0;
        boolean hasConfirmed = false;
        for (Passenger p : passengers) {
            if (p.isConfirmed()) {
                sum += p.getFare();
                hasConfirmed = true;
            }
        }
        this.totalFare = sum;
        if (!hasConfirmed) {
            this.status = "CANCELLED";
        }
    }

    public List<Passenger> getActivePassengers() {
        List<Passenger> active = new ArrayList<>();
        for (Passenger p : passengers) {
            if (p.isConfirmed()) {
                active.add(p);
            }
        }
        return active;
    }

    public List<String> getActiveSeatNumbers() {
        List<String> activeSeats = new ArrayList<>();
        for (Passenger p : passengers) {
            if (p.isConfirmed()) {
                activeSeats.add(p.getSeatNumber());
            }
        }
        return activeSeats;
    }

    public Passenger getPassengerForSeat(String seatNumber) {
        String normalized = Seat.normalize(seatNumber);
        for (Passenger p : passengers) {
            if (p.getSeatNumber().equalsIgnoreCase(normalized)) {
                return p;
            }
        }
        return null;
    }

    public boolean cancelSeat(String seatNumber) {
        Passenger p = getPassengerForSeat(seatNumber);
        if (p != null && p.isConfirmed()) {
            p.setStatus("CANCELLED");
            recalculateTotalFare();
            return true;
        }
        return false;
    }

    public void cancelAll() {
        for (Passenger p : passengers) {
            p.setStatus("CANCELLED");
        }
        this.totalFare = 0.0;
        this.status = "CANCELLED";
    }

    public void printReceipt(Bus bus) {
        System.out.println();
        System.out.println("==========================================================================");
        System.out.println("                           NAMMAROUTE TRAVELS                             ");
        System.out.println("                        OFFICIAL BOOKING RECEIPT                          ");
        System.out.println("==========================================================================");
        System.out.printf("PNR Number       : %s%n", pnr);
        System.out.printf("Booking Date/Time: %s%n", bookingTime.format(FORMATTER));
        System.out.printf("Booking Status   : %s%n", status);
        System.out.println("--------------------------------------------------------------------------");
        if (bus != null) {
            System.out.printf("Bus Route ID     : %s%n", bus.getRouteId());
            System.out.printf("Journey          : %s -> %s%n", bus.getOrigin(), bus.getDestination());
            System.out.printf("Departure Time   : %s%n", bus.getDepartureTime().format(Bus.DATE_TIME_FORMATTER));
            System.out.printf("Bus Type         : %s%n", bus.getBusType().getDisplayName());
            System.out.printf("Base Ticket Fare : Rs. %.2f%n", bus.getBasePrice());
        } else {
            System.out.printf("Bus Route ID     : %s%n", routeId);
        }
        System.out.println("--------------------------------------------------------------------------");
        System.out.println("PASSENGER DETAILS:");
        System.out.printf("%-4s %-8s %-26s %-6s %-8s %-12s %s%n",
                "No.", "Seat", "Passenger Name", "Age", "Gender", "Fare (INR)", "Status");
        System.out.println("--------------------------------------------------------------------------");
        int index = 1;
        for (Passenger p : passengers) {
            Seat s = bus != null ? bus.getSeat(p.getSeatNumber()) : null;
            String displaySeat = s != null ? s.getDisplayLabel() : p.getSeatNumber();
            System.out.printf("%-4d %-8s %-26s %-6d %-8s Rs. %-9.2f %s%n",
                    index++, displaySeat, p.getName(), p.getAge(), p.getGender(), p.getFare(), p.getStatus());
        }
        System.out.println("--------------------------------------------------------------------------");
        System.out.printf("Active Seats Booked : %d%n", getActivePassengers().size());
        System.out.printf("Total Amount Paid   : Rs. %.2f%n", totalFare);
        System.out.println("==========================================================================");
        System.out.println("    Thank you for choosing NammaRoute Travels! Have a safe journey!       ");
        System.out.println("==========================================================================");
        System.out.println();
    }

    public String getPnr() {
        return pnr;
    }

    public String getRouteId() {
        return routeId;
    }

    public LocalDateTime getBookingTime() {
        return bookingTime;
    }

    public List<Passenger> getPassengers() {
        return Collections.unmodifiableList(passengers);
    }

    public double getTotalFare() {
        return totalFare;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isConfirmed() {
        return "CONFIRMED".equalsIgnoreCase(status);
    }

    @Override
    public String toString() {
        return String.format("PNR: %s | Route: %s | Status: %s | Seats: %s | Fare: Rs. %.2f",
                pnr, routeId, status, getActiveSeatNumbers(), totalFare);
    }
}
