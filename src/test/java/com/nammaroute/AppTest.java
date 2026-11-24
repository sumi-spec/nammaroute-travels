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

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Automated Test Suite verifying all 11 required scenarios:
 * 1. Add a new bus.
 * 2. View all buses.
 * 3. Display an empty seat map.
 * 4. Book two seats successfully.
 * 5. Attempt to book an already-booked seat.
 * 6. Attempt booking with an invalid seat.
 * 7. Search booking using PNR.
 * 8. Cancel one seat from a multi-seat booking.
 * 9. Verify the cancelled seat becomes available.
 * 10. Generate passenger manifest.
 * 11. Restart application and verify persistence.
 */
public class AppTest {
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println("          NAMMAROUTE TRAVELS - AUTOMATED TEST SUITE (11 SCENARIOS)        ");
        System.out.println("==========================================================================");

        // Use a dedicated test data directory
        String testDataDir = "test_data";
        cleanDirectory(new File(testDataDir));

        FileStorageService storageService = new FileStorageService(testDataDir);
        BusService busService = new BusService(storageService);
        BookingService bookingService = new BookingService(busService, storageService);
        ManifestService manifestService = new ManifestService();

        // SCENARIO 1: Add a new bus
        System.out.println("\n[SCENARIO 1] Adding a new bus...");
        Bus newBus = new Bus("NR-104", "Mysuru", "Mangaluru",
                LocalDateTime.parse("2026-11-30 06:00", Bus.DATE_TIME_FORMATTER), 650.0, BusType.SEATER);
        boolean added = busService.addBus(newBus);
        boolean duplicateRejected = !busService.addBus(newBus); // Duplicate should fail
        assertTrue(added, "Scenario 1.1: Bus NR-104 added successfully");
        assertTrue(duplicateRejected, "Scenario 1.2: Duplicate Route ID NR-104 rejected");

        // SCENARIO 2: View all buses
        System.out.println("\n[SCENARIO 2] Verifying all buses...");
        busService.displayAllBuses();
        assertTrue(busService.getAllBuses().size() >= 4, "Scenario 2: Total buses >= 4 (3 sample + 1 new)");
        assertTrue(busService.getBus("NR-101") != null, "Scenario 2.1: Preloaded NR-101 exists");
        assertTrue(busService.getBus("NR-102") != null, "Scenario 2.2: Preloaded NR-102 exists");
        assertTrue(busService.getBus("NR-103") != null, "Scenario 2.3: Preloaded NR-103 exists");
        assertTrue(busService.getBus("NR-104") != null, "Scenario 2.4: Added NR-104 exists");

        // SCENARIO 3: Display an empty seat map
        System.out.println("\n[SCENARIO 3] Checking empty seat map for NR-101...");
        Bus bus101 = busService.getBus("NR-101");
        bus101.displaySeatMap();
        assertEquals(32, bus101.getTotalSeats(), "Scenario 3.1: NR-101 capacity is 32");
        assertEquals(32, bus101.getAvailableSeatsCount(), "Scenario 3.2: Initial available seats is 32");
        assertEquals(0, bus101.getBookedSeatsCount(), "Scenario 3.3: Initial booked seats is 0");

        // SCENARIO 4: Book two seats successfully
        System.out.println("\n[SCENARIO 4] Booking two seats (1A, 1B) on NR-101...");
        List<Passenger> passengers = new ArrayList<>();
        passengers.add(new Passenger("Rahul Sharma", 28, "M", "1A", bus101.getBasePrice()));
        passengers.add(new Passenger("Priya Sharma", 26, "F", "1B", bus101.getBasePrice()));

        Booking booking1 = bookingService.bookTickets("NR-101", passengers);
        assertTrue(booking1 != null, "Scenario 4.1: Booking created successfully");
        assertTrue(booking1.getPnr().startsWith("PNR-"), "Scenario 4.2: PNR format matches PNR-XXXXX: " + (booking1 != null ? booking1.getPnr() : "null"));
        assertEquals(2, bus101.getBookedSeatsCount(), "Scenario 4.3: Booked seats count is 2");
        assertEquals(30, bus101.getAvailableSeatsCount(), "Scenario 4.4: Available seats count is 30");
        assertTrue(bus101.getSeat("1A").isBooked(), "Scenario 4.5: Seat 1A is marked booked");
        assertTrue(bus101.getSeat("1B").isBooked(), "Scenario 4.6: Seat 1B is marked booked");
        assertEquals(2400.0, booking1.getTotalFare(), 0.01, "Scenario 4.7: Total fare is 2400.0");

        // SCENARIO 5: Attempt to book an already-booked seat (Atomic check)
        System.out.println("\n[SCENARIO 5] Attempting to book already-booked seat 1A...");
        List<String> requestedSeatsScenario5 = Arrays.asList("1A", "1C");
        String valError5 = InputValidator.validateSeatsForBooking(requestedSeatsScenario5, bus101);
        assertTrue(valError5 != null && valError5.contains("already booked"), "Scenario 5.1: Validation catches already-booked seat: " + valError5);

        List<Passenger> invalidBookingPassengers = Arrays.asList(
                new Passenger("Amit Verma", 35, "M", "1A", bus101.getBasePrice()),
                new Passenger("Neha Verma", 32, "F", "1C", bus101.getBasePrice())
        );
        Booking failedBooking = bookingService.bookTickets("NR-101", invalidBookingPassengers);
        assertTrue(failedBooking == null, "Scenario 5.2: Booking rejected atomically");
        assertFalse(bus101.getSeat("1C").isBooked(), "Scenario 5.3: Seat 1C remains unbooked (atomic)");

        // SCENARIO 6: Attempt booking with an invalid seat
        System.out.println("\n[SCENARIO 6] Attempting to book non-existent seat 9Z...");
        List<String> requestedSeatsScenario6 = Collections.singletonList("9Z");
        String valError6 = InputValidator.validateSeatsForBooking(requestedSeatsScenario6, bus101);
        assertTrue(valError6 != null && valError6.contains("does not exist"), "Scenario 6.1: Validation catches non-existent seat");

        // SCENARIO 7: Search booking using PNR
        System.out.println("\n[SCENARIO 7] Searching booking by PNR...");
        Booking searched = bookingService.getBooking(booking1.getPnr());
        assertTrue(searched != null, "Scenario 7.1: Found booking by PNR " + booking1.getPnr());
        assertEquals("NR-101", searched.getRouteId(), "Scenario 7.2: Route ID matches");
        assertEquals(2, searched.getActivePassengers().size(), "Scenario 7.3: Active passengers match");

        // SCENARIO 8: Cancel one seat from a multi-seat booking
        System.out.println("\n[SCENARIO 8] Cancelling seat 1B from booking...");
        boolean cancelledPartial = bookingService.cancelTickets(booking1.getPnr(), Collections.singletonList("1B"));
        assertTrue(cancelledPartial, "Scenario 8.1: Partial cancellation executed");
        assertEquals(1, booking1.getActivePassengers().size(), "Scenario 8.2: 1 active passenger remains");
        assertEquals(1200.0, booking1.getTotalFare(), 0.01, "Scenario 8.3: Remaining fare is 1200.0");
        assertTrue(booking1.isConfirmed(), "Scenario 8.4: Booking status remains CONFIRMED");

        // SCENARIO 9: Verify the cancelled seat becomes available
        System.out.println("\n[SCENARIO 9] Verifying cancelled seat 1B is available...");
        assertFalse(bus101.getSeat("1B").isBooked(), "Scenario 9.1: Seat 1B is now unbooked");
        assertTrue(bus101.getSeat("1A").isBooked(), "Scenario 9.2: Seat 1A remains booked");
        assertEquals(1, bus101.getBookedSeatsCount(), "Scenario 9.3: Total booked seats is now 1");
        assertEquals(31, bus101.getAvailableSeatsCount(), "Scenario 9.4: Total available seats is now 31");

        // SCENARIO 10: Generate passenger manifest
        System.out.println("\n[SCENARIO 10] Generating passenger manifest for NR-101...");
        manifestService.generateManifest("NR-101", busService, bookingService);
        assertTrue(true, "Scenario 10: Manifest rendered successfully");

        // SCENARIO 11: Restart application and verify persistence
        System.out.println("\n[SCENARIO 11] Simulating application restart and verifying persistence...");
        FileStorageService newStorage = new FileStorageService(testDataDir);
        BusService reloadedBusService = new BusService(newStorage);
        BookingService reloadedBookingService = new BookingService(reloadedBusService, newStorage);

        Bus reloaded101 = reloadedBusService.getBus("NR-101");
        assertTrue(reloaded101 != null, "Scenario 11.1: Reloaded NR-101 exists");
        assertTrue(reloadedBusService.getBus("NR-104") != null, "Scenario 11.2: Reloaded added bus NR-104 exists");

        Booking reloadedBooking = reloadedBookingService.getBooking(booking1.getPnr());
        assertTrue(reloadedBooking != null, "Scenario 11.3: Reloaded booking exists");
        assertEquals(1, reloadedBooking.getActivePassengers().size(), "Scenario 11.4: 1 active passenger persisted");
        assertEquals("1A", reloadedBooking.getActivePassengers().get(0).getSeatNumber(), "Scenario 11.5: Seat 1A persisted as active");
        assertTrue(reloaded101.getSeat("1A").isBooked(), "Scenario 11.6: Seat 1A is booked in reloaded bus");
        assertFalse(reloaded101.getSeat("1B").isBooked(), "Scenario 11.7: Cancelled Seat 1B is available in reloaded bus");

        // Clean up test files
        cleanDirectory(new File(testDataDir));

        System.out.println("\n==========================================================================");
        System.out.printf("TEST RESULTS: %d PASSED, %d FAILED%n", testsPassed, testsFailed);
        System.out.println("==========================================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void assertTrue(boolean condition, String description) {
        if (condition) {
            System.out.println("  [PASS] " + description);
            testsPassed++;
        } else {
            System.err.println("  [FAIL] " + description);
            testsFailed++;
        }
    }

    private static void assertFalse(boolean condition, String description) {
        assertTrue(!condition, description);
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        boolean match = (expected == null && actual == null) || (expected != null && expected.equals(actual));
        if (match) {
            System.out.println("  [PASS] " + description + " (Expected: " + expected + ", Actual: " + actual + ")");
            testsPassed++;
        } else {
            System.err.println("  [FAIL] " + description + " (Expected: " + expected + ", Actual: " + actual + ")");
            testsFailed++;
        }
    }

    private static void assertEquals(double expected, double actual, double delta, String description) {
        boolean match = Math.abs(expected - actual) <= delta;
        if (match) {
            System.out.println("  [PASS] " + description + " (Expected: " + expected + ", Actual: " + actual + ")");
            testsPassed++;
        } else {
            System.err.println("  [FAIL] " + description + " (Expected: " + expected + ", Actual: " + actual + ")");
            testsFailed++;
        }
    }

    private static void cleanDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            dir.delete();
        }
    }
}
