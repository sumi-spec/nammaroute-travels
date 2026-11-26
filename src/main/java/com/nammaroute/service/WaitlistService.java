package com.nammaroute.service;

import com.nammaroute.model.Booking;
import com.nammaroute.model.Bus;
import com.nammaroute.model.Passenger;
import com.nammaroute.model.Seat;
import com.nammaroute.model.WaitlistEntry;
import com.nammaroute.storage.FileStorageService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service managing FIFO Waitlist queues and smart safety auto-promotion (Version 2.0 Change 3).
 * When a bus reaches 100% capacity, up to 5 passengers can join the waitlist.
 * When a seat is cancelled, the first eligible passenger in queue is automatically promoted.
 */
public class WaitlistService {
    public static final int MAX_WAITLIST_CAPACITY = 5;

    private final Map<String, List<WaitlistEntry>> waitlistQueues;
    private final FileStorageService storageService;

    public WaitlistService(FileStorageService storageService) {
        this.storageService = storageService;
        this.waitlistQueues = new LinkedHashMap<>();
        loadWaitlist();
    }

    public void loadWaitlist() {
        waitlistQueues.clear();
        Map<String, List<WaitlistEntry>> loaded = storageService.loadWaitlist();
        waitlistQueues.putAll(loaded);
    }

    public int getWaitlistCount(String routeId) {
        if (routeId == null) return 0;
        List<WaitlistEntry> list = waitlistQueues.get(routeId.trim().toUpperCase());
        return list != null ? list.size() : 0;
    }

    public boolean isWaitlistFull(String routeId) {
        return getWaitlistCount(routeId) >= MAX_WAITLIST_CAPACITY;
    }

    public List<WaitlistEntry> getWaitlistQueue(String routeId) {
        if (routeId == null) return Collections.emptyList();
        List<WaitlistEntry> list = waitlistQueues.get(routeId.trim().toUpperCase());
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    public Map<String, List<WaitlistEntry>> getAllWaitlists() {
        return Collections.unmodifiableMap(waitlistQueues);
    }

    /**
     * Adds a passenger to the FIFO waitlist for a fully booked bus route.
     * Enforces MAX_WAITLIST_CAPACITY = 5.
     */
    public synchronized WaitlistEntry addToWaitlist(Bus bus, String name, int age, String gender) {
        if (bus == null) {
            System.out.println(">> Error: Invalid bus route.");
            return null;
        }

        String routeId = bus.getRouteId();
        if (bus.getAvailableSeatsCount() > 0) {
            System.out.printf(">> Notice: Bus %s still has %d available seat(s). Please book directly!%n",
                    routeId, bus.getAvailableSeatsCount());
            return null;
        }

        List<WaitlistEntry> queue = waitlistQueues.computeIfAbsent(routeId, k -> new ArrayList<>());
        if (queue.size() >= MAX_WAITLIST_CAPACITY) {
            System.out.printf(">> Error: Waitlist for route %s is FULL (5/5). Cannot accept more waitlist requests.%n", routeId);
            return null;
        }

        int position = queue.size() + 1;
        String waitlistId = String.format("WL-%s-%02d", routeId, position);
        WaitlistEntry entry = new WaitlistEntry(waitlistId, routeId, name, age, gender, LocalDateTime.now());
        queue.add(entry);

        // Persist to CSV
        storageService.saveWaitlist(waitlistQueues);

        // Print confirmation
        printWaitlistReceipt(entry, position);

        return entry;
    }

    /**
     * Checks the FIFO waitlist and automatically promotes the first eligible passenger to the freed seat.
     * Smart Safety Check:
     * - If the freed seat is adjacent to a female passenger, Person #1 (if Male) is skipped
     *   and kept at the front of the queue, while Person #2 (if Female) is promoted!
     */
    public synchronized WaitlistEntry promoteNextEligiblePassenger(Bus bus, String freedSeat, BookingService bookingService) {
        if (bus == null || freedSeat == null || bookingService == null) {
            return null;
        }

        String routeId = bus.getRouteId();
        List<WaitlistEntry> queue = waitlistQueues.get(routeId);
        if (queue == null || queue.isEmpty()) {
            return null; // No one is waiting
        }

        Seat seatObj = bus.getSeat(freedSeat);
        String displaySeat = seatObj != null ? seatObj.getDisplayLabel() : freedSeat;

        // Iterate through FIFO queue to find first eligible passenger
        for (int i = 0; i < queue.size(); i++) {
            WaitlistEntry candidate = queue.get(i);

            // Change 2 & 3 Smart Safety Check
            boolean isSafe = CoSeatSafetyService.isSeatSafeForPassenger(bus, freedSeat, candidate.getPassengerGender(), bookingService);

            if (isSafe) {
                // Remove candidate from waitlist queue
                queue.remove(i);
                storageService.saveWaitlist(waitlistQueues);

                // Re-book freed seat on the bus
                bus.bookSeat(freedSeat);

                // Calculate fare (uses dynamic pricing if applicable)
                double fare = bus.getCurrentSeatPrice();

                // Create confirmed passenger and booking
                String pnr = bookingService.generateUniquePnr();
                Passenger p = new Passenger(candidate.getPassengerName(), candidate.getPassengerAge(),
                        candidate.getPassengerGender(), freedSeat, fare, "CONFIRMED");
                List<Passenger> pList = new ArrayList<>();
                pList.add(p);

                Booking newBooking = new Booking(pnr, routeId, LocalDateTime.now(), pList, fare, "CONFIRMED");
                bookingService.addConfirmedBooking(newBooking);
                storageService.saveBookings(bookingService.getAllBookings());

                // Display Auto-Promotion Alert Banner
                System.out.println();
                System.out.println("**************************************************************************");
                System.out.println("            [⚡ AUTO-PROMOTION ALERT] WAITLIST PASSENGER PROMOTED!        ");
                System.out.println("**************************************************************************");
                System.out.printf("Passenger Promoted : %s (Age: %d, Gender: %s)%n",
                        candidate.getPassengerName(), candidate.getPassengerAge(), candidate.getPassengerGender());
                System.out.printf("Route / Journey    : %s (%s -> %s)%n",
                        bus.getRouteId(), bus.getOrigin(), bus.getDestination());
                System.out.printf("Assigned Seat      : %s%n", displaySeat);
                System.out.printf("New Confirmed PNR  : %s%n", pnr);
                System.out.printf("Ticket Fare        : Rs. %.2f%n", fare);
                System.out.printf("Queue Status       : Removed from FIFO Queue (%d remaining on waitlist)%n", queue.size());
                System.out.println("**************************************************************************");
                System.out.println();

                return candidate;
            } else {
                // Smart Safety Notice: Skip candidate for this seat, keep them in queue
                System.out.println();
                System.out.printf(">> [SMART SAFETY NOTICE] Skipping waitlist passenger %s (%s) for seat %s%n",
                        candidate.getPassengerName(), candidate.getPassengerGender(), displaySeat);
                System.out.println("   Reason: Adjacent seat is occupied by a solo female passenger.");
                System.out.println("   Candidate remains at the front of the queue for the next available seat.");
            }
        }

        return null; // No eligible candidate in queue for this specific seat
    }

    private void printWaitlistReceipt(WaitlistEntry entry, int position) {
        System.out.println();
        System.out.println("==========================================================================");
        System.out.println("                           NAMMAROUTE TRAVELS                             ");
        System.out.println("                       OFFICIAL WAITLIST TICKET                           ");
        System.out.println("==========================================================================");
        System.out.printf("Waitlist ID        : %s%n", entry.getWaitlistId());
        System.out.printf("Route ID           : %s%n", entry.getRouteId());
        System.out.printf("Passenger Name     : %s%n", entry.getPassengerName());
        System.out.printf("Age / Gender       : %d / %s%n", entry.getPassengerAge(), entry.getPassengerGender());
        System.out.printf("Queue Position     : #%d of %d in line (FIFO Queue)%n", position, MAX_WAITLIST_CAPACITY);
        System.out.printf("Request Timestamp  : %s%n", entry.getRequestTime().format(WaitlistEntry.FORMATTER));
        System.out.println("--------------------------------------------------------------------------");
        System.out.println("NOTE: You will be automatically assigned a seat and issued a confirmed    ");
        System.out.println("      PNR immediately if an eligible passenger cancels their booking.     ");
        System.out.println("==========================================================================");
        System.out.println();
    }

    public void displayWaitlist(String routeId) {
        System.out.println();
        System.out.println("==========================================================================");
        System.out.printf("                 FIFO WAITLIST QUEUE - ROUTE: %s%n", routeId != null ? routeId.toUpperCase() : "ALL");
        System.out.println("==========================================================================");
        List<WaitlistEntry> list = getWaitlistQueue(routeId);
        if (list.isEmpty()) {
            System.out.println(">> No passengers currently on the waitlist for route " + routeId);
        } else {
            System.out.printf("%-6s %-14s %-24s %-6s %-8s %s%n",
                    "Pos", "Waitlist ID", "Passenger Name", "Age", "Gender", "Requested Time");
            System.out.println("--------------------------------------------------------------------------");
            int pos = 1;
            for (WaitlistEntry w : list) {
                System.out.printf("#%-5d %-14s %-24s %-6d %-8s %s%n",
                        pos++, w.getWaitlistId(), w.getPassengerName(), w.getPassengerAge(),
                        w.getPassengerGender(), w.getRequestTime().format(WaitlistEntry.FORMATTER));
            }
        }
        System.out.println("==========================================================================");
        System.out.println();
    }
}

