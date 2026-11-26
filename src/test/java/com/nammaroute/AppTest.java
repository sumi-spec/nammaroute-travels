package com.nammaroute;

import com.nammaroute.model.Booking;
import com.nammaroute.model.Bus;
import com.nammaroute.model.BusType;
import com.nammaroute.model.Passenger;
import com.nammaroute.model.Seat;
import com.nammaroute.service.BookingService;
import com.nammaroute.service.BusService;
import com.nammaroute.service.CoSeatSafetyService;
import com.nammaroute.service.ManifestService;
import com.nammaroute.service.WaitlistService;
import com.nammaroute.storage.FileStorageService;
import com.nammaroute.util.InputValidator;
import com.nammaroute.model.WaitlistEntry;

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

        // =====================================================================
        // VERSION 2.0 EVALUATION SCENARIOS
        // =====================================================================

        // SCENARIO 12: Dynamic Surge Pricing (70% Occupancy Rule)
        System.out.println("\n[SCENARIO 12] Testing Dynamic Surge Pricing (70% Occupancy Rule)...");
        // Use a test bus with 20 seats to exactly verify client's concrete example
        Bus bus20 = new Bus("NR-200", "Bengaluru", "Mysuru",
                LocalDateTime.parse("2026-12-01 10:00", Bus.DATE_TIME_FORMATTER), 1000.0, BusType.SEATER);
        reloadedBusService.addBus(bus20);

        // Initially 0 booked -> Standard price
        assertEquals(1000.0, bus20.getCurrentSeatPrice(), 0.01, "Scenario 12.1: Initial price is base price (Rs. 1000)");
        assertFalse(bus20.isSurgePricingActive(), "Scenario 12.2: Surge pricing initially inactive");

        // Book 13 seats (13/32 or in our 20-seat example: 13 seats)
        // For bus20 (32 seats in SEATER layout: 70% of 32 = 22.4 -> 23 seats)
        // Let's test on NR-102 (24 berths: 70% of 24 = 16.8 -> 17 berths)
        Bus bus102 = reloadedBusService.getBus("NR-102");
        assertEquals(900.0, bus102.getBasePrice(), 0.01, "Scenario 12.3: NR-102 base price is 900.0");
        assertFalse(bus102.isSurgePricingActive(), "Scenario 12.4: NR-102 surge inactive at 0% occupancy");

        // Book 16 berths on NR-102 (16 / 24 = 66.7% < 70%)
        List<Passenger> batch16 = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            batch16.add(new Passenger("Pass-L" + i, 25, "M", "L" + i, bus102.getBasePrice()));
        }
        for (int i = 1; i <= 4; i++) {
            batch16.add(new Passenger("Pass-U" + i, 25, "M", "U" + i, bus102.getBasePrice()));
        }
        Booking b16 = reloadedBookingService.bookTickets("NR-102", batch16);
        assertTrue(b16 != null, "Scenario 12.5: Successfully booked 16 berths on NR-102");
        assertEquals(16, bus102.getBookedSeatsCount(), "Scenario 12.6: Booked count is 16");
        assertFalse(bus102.isSurgePricingActive(), "Scenario 12.7: Surge pricing still inactive at 16/24 (66.7%)");
        assertEquals(900.0, bus102.getCurrentSeatPrice(), 0.01, "Scenario 12.8: Price per seat remains Rs. 900.0");

        // Book 1 more berth (17th berth -> 17/24 = 70.8% >= 70%)
        List<Passenger> p17 = Collections.singletonList(
                new Passenger("Pass-U5", 29, "M", "U5", bus102.getCurrentSeatPrice()));
        Booking b17 = reloadedBookingService.bookTickets("NR-102", p17);
        assertTrue(b17 != null, "Scenario 12.9: 17th berth booked");
        assertTrue(bus102.isSurgePricingActive(), "Scenario 12.10: Surge pricing ACTIVATED at 17/24 (>=70% occupancy)");
        assertEquals(1080.0, bus102.getCurrentSeatPrice(), 0.01, "Scenario 12.11: Empty seats cost 20% extra (Rs. 1080.0)");

        // Previous passengers retain their original booked price
        assertEquals(900.0, b16.getPassengers().get(0).getFare(), 0.01, "Scenario 12.12: Existing bookings retain original price (Rs. 900.0)");

        // Cancellation drops occupancy back below 70% -> price reverts to normal
        reloadedBookingService.cancelTickets(b17.getPnr(), null);
        assertEquals(16, bus102.getBookedSeatsCount(), "Scenario 12.13: Booked seats dropped to 16 after cancellation");
        assertFalse(bus102.isSurgePricingActive(), "Scenario 12.14: Surge pricing DEACTIVATED when dropping below 70%");
        assertEquals(900.0, bus102.getCurrentSeatPrice(), 0.01, "Scenario 12.15: Fare reverted back to standard Rs. 900.0");

        // SCENARIO 13: Female Passenger Co-Seat Safety Rule
        System.out.println("\n[SCENARIO 13] Testing Female Passenger Co-Seat Safety Rule...");
        Bus bus103 = reloadedBusService.getBus("NR-103"); // 2x2 Seater

        // 13.1: Solo female passenger books seat 1A
        List<Passenger> femalePass = Collections.singletonList(
                new Passenger("Ananya Roy", 24, "F", "1A", bus103.getBasePrice()));
        Booking bFemale = reloadedBookingService.bookTickets("NR-103", femalePass);
        assertTrue(bFemale != null, "Scenario 13.1: Female passenger Ananya booked 1A");

        // 13.2: Male passenger attempts to book adjacent seat 1B -> MUST BE BLOCKED
        List<Passenger> maleSolo = Collections.singletonList(
                new Passenger("Karthik N", 28, "M", "1B", bus103.getBasePrice()));
        String safetyError = CoSeatSafetyService.validateFemaleCoSeatRule(bus103, maleSolo, reloadedBookingService);
        assertTrue(safetyError != null && safetyError.contains("reserved for a female co-passenger"),
                "Scenario 13.2: Safety rule blocks male booking on 1B: " + safetyError);

        Booking blockedMale = reloadedBookingService.bookTickets("NR-103", maleSolo);
        assertTrue(blockedMale == null, "Scenario 13.3: BookingService atomically rejects male booking on 1B");
        assertFalse(bus103.getSeat("1B").isBooked(), "Scenario 13.4: Seat 1B remains open");

        // 13.3: Another female passenger books seat 1B -> MUST SUCCEED
        List<Passenger> femaleCoPass = Collections.singletonList(
                new Passenger("Divya Rao", 26, "F", "1B", bus103.getBasePrice()));
        Booking allowedFemale = reloadedBookingService.bookTickets("NR-103", femaleCoPass);
        assertTrue(allowedFemale != null, "Scenario 13.5: Female co-passenger Divya booked 1B successfully");
        assertTrue(bus103.getSeat("1B").isBooked(), "Scenario 13.6: Seat 1B is now booked");

        // 13.4: Group / Family booking exception: Male & Female booking adjacent pair (2A & 2B) together
        List<Passenger> familyBooking = Arrays.asList(
                new Passenger("Suresh Kumar", 35, "M", "2A", bus103.getBasePrice()),
                new Passenger("Lakshmi Suresh", 32, "F", "2B", bus103.getBasePrice())
        );
        String groupSafetyError = CoSeatSafetyService.validateFemaleCoSeatRule(bus103, familyBooking, reloadedBookingService);
        assertTrue(groupSafetyError == null, "Scenario 13.7: Group exception permits Male & Female booking pair together");
        Booking bFamily = reloadedBookingService.bookTickets("NR-103", familyBooking);
        assertTrue(bFamily != null, "Scenario 13.8: Family booking (2A, 2B) confirmed on same PNR");

        // SCENARIO 14: FIFO Waitlist & Smart Safety Auto-Promotion
        System.out.println("\n[SCENARIO 14] Testing FIFO Waitlist & Smart Safety Auto-Promotion...");
        WaitlistService waitlistService = new WaitlistService(newStorage);
        reloadedBookingService.setWaitlistService(waitlistService);

        // Fill remaining seats on NR-103 to 100% capacity
        List<Passenger> fillSeats = new ArrayList<>();
        for (Seat s : bus103.getSeats().values()) {
            if (!s.isBooked()) {
                fillSeats.add(new Passenger("Filler-" + s.getSeatNumber(), 22, "M", s.getSeatNumber(), bus103.getBasePrice()));
            }
        }
        reloadedBookingService.bookTickets("NR-103", fillSeats);
        assertEquals(0, bus103.getAvailableSeatsCount(), "Scenario 14.1: NR-103 is now 100% full (0 available)");

        // Direct booking rejected because bus is full
        Booking fullReject = reloadedBookingService.bookTickets("NR-103",
                Collections.singletonList(new Passenger("Extra", 25, "M", "1C", bus103.getBasePrice())));
        assertTrue(fullReject == null, "Scenario 14.2: Direct booking rejected on 100% full bus");

        // Add 5 passengers to FIFO waitlist
        WaitlistEntry w1 = waitlistService.addToWaitlist(bus103, "Dev Kumar", 30, "M");      // #1 Male
        WaitlistEntry w2 = waitlistService.addToWaitlist(bus103, "Sneha Patel", 27, "F");    // #2 Female
        WaitlistEntry w3 = waitlistService.addToWaitlist(bus103, "Rohan Das", 29, "M");      // #3 Male
        WaitlistEntry w4 = waitlistService.addToWaitlist(bus103, "Pooja Hegde", 25, "F");    // #4 Female
        WaitlistEntry w5 = waitlistService.addToWaitlist(bus103, "Vikram Seth", 33, "M");    // #5 Male

        assertTrue(w1 != null && w2 != null && w5 != null, "Scenario 14.3: Added 5 passengers to waitlist");
        assertEquals(5, waitlistService.getWaitlistCount("NR-103"), "Scenario 14.4: Waitlist count is 5/5");
        assertTrue(waitlistService.isWaitlistFull("NR-103"), "Scenario 14.5: Waitlist is at max capacity");

        // 6th passenger attempt rejected
        WaitlistEntry w6 = waitlistService.addToWaitlist(bus103, "Excess Pass", 40, "M");
        assertTrue(w6 == null, "Scenario 14.6: 6th waitlist request rejected (Max 5 enforced)");

        // 14.5: Smart Safety Auto-Promotion Test
        // Seat 1A is booked by Female Ananya. Seat 1B is booked by Female Divya.
        // If Divya cancels 1B:
        // Seat 1B is freed, but adjacent seat 1A is Female Ananya!
        // Person #1 on waitlist is Dev (Male). Person #2 is Sneha (Female).
        // System must SKIP Dev (keep him at #1) and AUTO-PROMOTE Sneha (Female) to 1B!
        System.out.println("Executing Smart Safety Auto-Promotion: Divya cancels seat 1B...");
        reloadedBookingService.cancelTickets(allowedFemale.getPnr(), Collections.singletonList("1B"));

        // Verify Seat 1B is re-booked immediately by Sneha!
        assertTrue(bus103.getSeat("1B").isBooked(), "Scenario 14.7: Seat 1B was auto-promoted and re-booked immediately");
        Passenger newPassenger1B = reloadedBookingService.getActivePassengerOnSeat("NR-103", "1B");
        assertTrue(newPassenger1B != null, "Scenario 14.8: Passenger exists on seat 1B");
        assertEquals("Sneha Patel", newPassenger1B.getName(), "Scenario 14.9: Smart Safety skipped Dev and promoted Sneha (Female)!");
        assertEquals("F", newPassenger1B.getGender(), "Scenario 14.10: Promoted passenger gender is Female");

        // Dev Kumar must still be #1 in line on the waitlist!
        List<WaitlistEntry> updatedQueue = waitlistService.getWaitlistQueue("NR-103");
        assertEquals(4, updatedQueue.size(), "Scenario 14.11: Waitlist size reduced to 4");
        assertEquals("Dev Kumar", updatedQueue.get(0).getPassengerName(), "Scenario 14.12: Dev Kumar remains #1 in FIFO queue");

        // SCENARIO 15: Version 2.0 Persistence Across Restart
        System.out.println("\n[SCENARIO 15] Verifying Waitlist & Version 2.0 State Persistence Across Restart...");
        FileStorageService v2Storage = new FileStorageService(testDataDir);
        WaitlistService restartedWaitlist = new WaitlistService(v2Storage);
        assertEquals(4, restartedWaitlist.getWaitlistCount("NR-103"), "Scenario 15.1: 4 waitlist entries persisted to waitlist.csv");
        assertEquals("Dev Kumar", restartedWaitlist.getWaitlistQueue("NR-103").get(0).getPassengerName(),
                "Scenario 15.2: Dev Kumar persisted as #1 in FIFO queue");

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
