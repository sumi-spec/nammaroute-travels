package com.nammaroute;

import com.nammaroute.model.Booking;
import com.nammaroute.model.Bus;
import com.nammaroute.model.BusType;
import com.nammaroute.model.Passenger;
import com.nammaroute.model.Seat;
import com.nammaroute.service.BookingService;
import com.nammaroute.service.BusService;
import com.nammaroute.service.ManifestService;
import com.nammaroute.storage.FileStorageService;
import com.nammaroute.util.InputValidator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Main application entry point for NammaRoute Travels - Bus Booking Management System.
 */
public class Main {
    private final BusService busService;
    private final BookingService bookingService;
    private final ManifestService manifestService;
    private final Scanner scanner;

    public Main() {
        FileStorageService storageService = new FileStorageService("data");
        this.busService = new BusService(storageService);
        this.bookingService = new BookingService(busService, storageService);
        this.manifestService = new ManifestService();
        this.scanner = new Scanner(System.in);
    }

    public static void main(String[] args) {
        Main app = new Main();
        app.run();
    }

    public void run() {
        boolean running = true;
        while (running) {
            try {
                printMenu();
                System.out.print("Enter your choice: ");
                String input = scanner.nextLine();
                if (input == null) break;

                input = input.trim();
                switch (input) {
                    case "1":
                        handleAddNewBus();
                        break;
                    case "2":
                        handleViewAllBuses();
                        break;
                    case "3":
                        handleViewSeatAvailability();
                        break;
                    case "4":
                        handleBookTickets();
                        break;
                    case "5":
                        handleCancelTickets();
                        break;
                    case "6":
                        handleGenerateManifest();
                        break;
                    case "7":
                        handleSearchBookingByPnr();
                        break;
                    case "8":
                        System.out.println("\nThank you for using NammaRoute Travels! Have a great day.");
                        running = false;
                        break;
                    default:
                        System.out.println("\n>> Error: Invalid choice. Please enter a number between 1 and 8.\n");
                        break;
                }
            } catch (NoSuchElementException | IllegalStateException e) {
                // Occurs when input stream is closed (e.g. piped input ended)
                break;
            } catch (Exception e) {
                System.out.println("\n>> An unexpected error occurred: " + e.getMessage() + "\n");
            }
        }
    }

    private void printMenu() {
        System.out.println("========================================");
        System.out.println("       NAMMAROUTE TRAVELS               ");
        System.out.println("       BUS BOOKING SYSTEM               ");
        System.out.println("========================================");
        System.out.println();
        System.out.println("1. Add New Bus");
        System.out.println("2. View All Buses");
        System.out.println("3. View Seat Availability");
        System.out.println("4. Book Tickets");
        System.out.println("5. Cancel Tickets");
        System.out.println("6. Generate Passenger Manifest");
        System.out.println("7. Search Booking by PNR");
        System.out.println("8. Exit");
        System.out.println();
    }

    /**
     * Feature 1: Add New Bus
     */
    private void handleAddNewBus() {
        System.out.println("\n--- [1] ADD NEW BUS SERVICE ---");
        String routeId = InputValidator.readNonEmptyString(scanner, "Enter Route ID (e.g. NR-104): ", "Route ID").toUpperCase();

        if (busService.getBus(routeId) != null) {
            System.out.println(">> Error: Route ID '" + routeId + "' already exists! Please use a unique Route ID.\n");
            return;
        }

        String origin = InputValidator.readNonEmptyString(scanner, "Enter Origin City (e.g. Mysuru): ", "Origin");
        String destination = InputValidator.readNonEmptyString(scanner, "Enter Destination City (e.g. Mangaluru): ", "Destination");
        LocalDateTime departureTime = InputValidator.readDateTime(scanner, "Enter Departure Date and Time (yyyy-MM-dd HH:mm): ");
        double basePrice = InputValidator.readPositiveDouble(scanner, "Enter Base Ticket Fare (INR): ", "Ticket Fare");
        BusType busType = InputValidator.readBusType(scanner, "Select Bus Type [1 for SEATER (2x2, 32 seats) | 2 for SLEEPER (2x1, 24 berths)]: ");

        Bus newBus = new Bus(routeId, origin, destination, departureTime, basePrice, busType);
        busService.addBus(newBus);
        System.out.println();
    }

    /**
     * Feature 1: View All Buses
     */
    private void handleViewAllBuses() {
        busService.displayAllBuses();
    }

    /**
     * Feature 2: View Seat Availability
     */
    private void handleViewSeatAvailability() {
        System.out.println("\n--- [3] VIEW SEAT AVAILABILITY ---");
        String routeId = InputValidator.readNonEmptyString(scanner, "Enter Route ID: ", "Route ID");
        busService.displaySeatMap(routeId);
    }

    /**
     * Feature 3: Book Tickets and Generate PNR
     */
    private void handleBookTickets() {
        System.out.println("\n--- [4] BOOK TICKETS ---");
        String routeId = InputValidator.readNonEmptyString(scanner, "Enter Route ID: ", "Route ID");
        Bus bus = busService.getBus(routeId);
        if (bus == null) {
            System.out.println(">> Error: Bus with Route ID '" + routeId + "' not found.\n");
            return;
        }

        if (bus.getAvailableSeatsCount() == 0) {
            System.out.println(">> Sorry, bus route '" + routeId + "' is fully booked!\n");
            return;
        }

        // 1. Display seat map
        bus.displaySeatMap();

        // 2. Prompt for seats
        System.out.println("Enter seat number(s) to book (comma or space separated, e.g. 1A, 1B or L1, L2):");
        System.out.print("Seats: ");
        String seatInput = scanner.nextLine();
        List<String> requestedSeats = InputValidator.parseSeatInput(seatInput);

        // 3. Atomic validation of all requested seats
        String validationError = InputValidator.validateSeatsForBooking(requestedSeats, bus);
        if (validationError != null) {
            System.out.println("\n>> Booking Failed: " + validationError);
            System.out.println(">> No seats were booked. Please try again.\n");
            return;
        }

        // 4. Collect passenger details for each seat
        System.out.printf("%nRequested %d seat(s): %s%n", requestedSeats.size(), requestedSeats);
        System.out.println("Please enter passenger details for each seat:");

        List<Passenger> passengerList = new ArrayList<>();
        int count = 1;
        for (String seatNum : requestedSeats) {
            Seat seat = bus.getSeat(seatNum);
            String displaySeat = seat != null ? seat.getDisplayLabel() : seatNum;
            System.out.printf("%n--- Passenger %d (Assigned Seat: %s) ---%n", count++, displaySeat);
            String name = InputValidator.readNonEmptyString(scanner, "Full Name: ", "Passenger Name");
            int age = InputValidator.readIntInRange(scanner, "Age (1-120): ", 1, 120, "Age");
            String gender = InputValidator.readGender(scanner, "Gender (M/F/Other): ");

            Passenger p = new Passenger(name, age, gender, seatNum, bus.getBasePrice());
            passengerList.add(p);
        }

        // 5. Execute atomic booking
        bookingService.bookTickets(routeId, passengerList);
    }

    /**
     * Feature 4: Cancel Tickets
     */
    private void handleCancelTickets() {
        System.out.println("\n--- [5] CANCEL TICKETS ---");
        String pnr = InputValidator.readNonEmptyString(scanner, "Enter PNR Number to cancel: ", "PNR").toUpperCase();
        Booking booking = bookingService.getBooking(pnr);
        if (booking == null) {
            System.out.println(">> Error: No booking found with PNR '" + pnr + "'.\n");
            return;
        }

        if (!booking.isConfirmed()) {
            System.out.println(">> Error: Booking '" + pnr + "' is already fully CANCELLED.\n");
            return;
        }

        List<String> activeSeats = booking.getActiveSeatNumbers();
        System.out.println("Active seats in this booking: " + activeSeats);
        System.out.print("Do you want to cancel the entire booking (ALL) or specific seats? [Enter 'ALL' or comma-separated seats (e.g. 1A)]: ");
        String cancelChoice = scanner.nextLine();
        if (cancelChoice == null || cancelChoice.trim().isEmpty()) {
            System.out.println(">> Cancellation cancelled: No input provided.\n");
            return;
        }

        cancelChoice = cancelChoice.trim();
        if (cancelChoice.equalsIgnoreCase("ALL")) {
            bookingService.cancelTickets(pnr, null);
        } else {
            List<String> seatsToCancel = InputValidator.parseSeatInput(cancelChoice);
            if (seatsToCancel.isEmpty()) {
                System.out.println(">> Error: No valid seat numbers entered.\n");
                return;
            }
            bookingService.cancelTickets(pnr, seatsToCancel);
        }
    }

    /**
     * Feature 5: Generate Passenger Manifest
     */
    private void handleGenerateManifest() {
        System.out.println("\n--- [6] GENERATE PASSENGER MANIFEST ---");
        String routeId = InputValidator.readNonEmptyString(scanner, "Enter Route ID: ", "Route ID");
        manifestService.generateManifest(routeId, busService, bookingService);
    }

    /**
     * Feature 7: Search Booking by PNR
     */
    private void handleSearchBookingByPnr() {
        System.out.println("\n--- [7] SEARCH BOOKING BY PNR ---");
        String pnr = InputValidator.readNonEmptyString(scanner, "Enter PNR Number: ", "PNR");
        bookingService.searchBookingByPnr(pnr);
    }
}
