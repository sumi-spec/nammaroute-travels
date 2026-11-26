package com.nammaroute.storage;

import com.nammaroute.model.Booking;
import com.nammaroute.model.Bus;
import com.nammaroute.model.BusType;
import com.nammaroute.model.Passenger;
import com.nammaroute.model.WaitlistEntry;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for persisting and loading application data using CSV files.
 */
public class FileStorageService {
    private final String dataDirPath;
    private final String busesFilePath;
    private final String bookingsFilePath;
    private final String waitlistFilePath;

    private static final String BUSES_HEADER = "RouteId,Origin,Destination,DepartureTime,BasePrice,BusType";
    private static final String BOOKINGS_HEADER = "PNR,RouteId,BookingTime,SeatNumber,PassengerName,PassengerAge,PassengerGender,Fare,Status";
    private static final String WAITLIST_HEADER = "RouteId,WaitlistId,PassengerName,PassengerAge,PassengerGender,RequestTime";

    public FileStorageService() {
        this("data");
    }

    public FileStorageService(String dataDir) {
        this.dataDirPath = dataDir;
        this.busesFilePath = dataDir + File.separator + "buses.csv";
        this.bookingsFilePath = dataDir + File.separator + "bookings.csv";
        this.waitlistFilePath = dataDir + File.separator + "waitlist.csv";
        initializeStorage();
    }

    /**
     * Ensures data directory and CSV files exist.
     */
    public void initializeStorage() {
        try {
            Path dir = Paths.get(dataDirPath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            File busesFile = new File(busesFilePath);
            if (!busesFile.exists()) {
                busesFile.createNewFile();
            }

            File bookingsFile = new File(bookingsFilePath);
            if (!bookingsFile.exists()) {
                bookingsFile.createNewFile();
            }

            File waitlistFile = new File(waitlistFilePath);
            if (!waitlistFile.exists()) {
                waitlistFile.createNewFile();
            }
        } catch (IOException e) {
            System.err.println("Warning: Unable to initialize data storage directory: " + e.getMessage());
        }
    }

    /**
     * Loads buses from buses.csv. If empty, preloads default sample buses.
     */
    public Map<String, Bus> loadBuses() {
        Map<String, Bus> buses = new LinkedHashMap<>();
        File file = new File(busesFilePath);

        if (!file.exists() || file.length() == 0) {
            preloadSampleBuses(buses);
            saveBuses(buses.values());
            return buses;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine(); // Read header
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                List<String> tokens = parseCsvLine(line);
                if (tokens.size() >= 6) {
                    try {
                        String routeId = tokens.get(0).trim().toUpperCase();
                        String origin = tokens.get(1).trim();
                        String destination = tokens.get(2).trim();
                        LocalDateTime departure = LocalDateTime.parse(tokens.get(3).trim(), Bus.DATE_TIME_FORMATTER);
                        double price = Double.parseDouble(tokens.get(4).trim());
                        BusType type = BusType.fromString(tokens.get(5).trim());
                        if (type != null) {
                            Bus bus = new Bus(routeId, origin, destination, departure, price, type);
                            buses.put(routeId, bus);
                        }
                    } catch (Exception ex) {
                        System.err.println("Warning: Skipping corrupted bus record: " + line + " (" + ex.getMessage() + ")");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading buses file: " + e.getMessage());
        }

        if (buses.isEmpty()) {
            preloadSampleBuses(buses);
            saveBuses(buses.values());
        }

        return buses;
    }

    /**
     * Preloads mandatory sample demo buses.
     */
    private void preloadSampleBuses(Map<String, Bus> buses) {
        // NR-101 | Bengaluru to Goa | 2026-11-27 21:30 | 1200 | SEATER
        Bus b1 = new Bus("NR-101", "Bengaluru", "Goa",
                LocalDateTime.parse("2026-11-27 21:30", Bus.DATE_TIME_FORMATTER), 1200.0, BusType.SEATER);
        // NR-102 | Bengaluru to Hyderabad | 2026-11-28 20:00 | 900 | SLEEPER
        Bus b2 = new Bus("NR-102", "Bengaluru", "Hyderabad",
                LocalDateTime.parse("2026-11-28 20:00", Bus.DATE_TIME_FORMATTER), 900.0, BusType.SLEEPER);
        // NR-103 | Bengaluru to Chennai | 2026-11-29 22:00 | 800 | SEATER
        Bus b3 = new Bus("NR-103", "Bengaluru", "Chennai",
                LocalDateTime.parse("2026-11-29 22:00", Bus.DATE_TIME_FORMATTER), 800.0, BusType.SEATER);

        buses.put(b1.getRouteId(), b1);
        buses.put(b2.getRouteId(), b2);
        buses.put(b3.getRouteId(), b3);
    }

    /**
     * Saves all buses to buses.csv.
     */
    public synchronized boolean saveBuses(Collection<Bus> buses) {
        File file = new File(busesFilePath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            writer.write(BUSES_HEADER);
            writer.newLine();
            for (Bus bus : buses) {
                String line = String.format("%s,%s,%s,%s,%.2f,%s",
                        escapeCsv(bus.getRouteId()),
                        escapeCsv(bus.getOrigin()),
                        escapeCsv(bus.getDestination()),
                        bus.getDepartureTime().format(Bus.DATE_TIME_FORMATTER),
                        bus.getBasePrice(),
                        bus.getBusType().name());
                writer.write(line);
                writer.newLine();
            }
            return true;
        } catch (IOException e) {
            System.err.println("Error saving buses: " + e.getMessage());
            return false;
        }
    }

    /**
     * Loads bookings from bookings.csv and marks corresponding bus seats as booked.
     */
    public Map<String, Booking> loadBookings(Map<String, Bus> buses) {
        Map<String, Booking> bookings = new LinkedHashMap<>();
        File file = new File(bookingsFilePath);

        if (!file.exists() || file.length() == 0) {
            return bookings;
        }

        // Temporary structure to group passengers by PNR
        Map<String, List<Passenger>> bookingPassengers = new LinkedHashMap<>();
        Map<String, String> bookingRoutes = new LinkedHashMap<>();
        Map<String, LocalDateTime> bookingTimes = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine(); // Header
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                List<String> tokens = parseCsvLine(line);
                if (tokens.size() >= 9) {
                    try {
                        String pnr = tokens.get(0).trim().toUpperCase();
                        String routeId = tokens.get(1).trim().toUpperCase();
                        LocalDateTime bookingTime = LocalDateTime.parse(tokens.get(2).trim(), Booking.FORMATTER);
                        String seatNumber = tokens.get(3).trim();
                        String name = tokens.get(4).trim();
                        int age = Integer.parseInt(tokens.get(5).trim());
                        String gender = tokens.get(6).trim();
                        double fare = Double.parseDouble(tokens.get(7).trim());
                        String status = tokens.get(8).trim().toUpperCase();

                        Passenger passenger = new Passenger(name, age, gender, seatNumber, fare, status);

                        bookingPassengers.computeIfAbsent(pnr, k -> new ArrayList<>()).add(passenger);
                        bookingRoutes.put(pnr, routeId);
                        bookingTimes.put(pnr, bookingTime);

                        // If confirmed, mark seat as booked on the bus
                        if ("CONFIRMED".equalsIgnoreCase(status) && buses.containsKey(routeId)) {
                            buses.get(routeId).bookSeat(seatNumber);
                        }
                    } catch (Exception ex) {
                        System.err.println("Warning: Skipping corrupted booking record: " + line + " (" + ex.getMessage() + ")");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading bookings file: " + e.getMessage());
        }

        // Reconstruct Booking objects
        for (Map.Entry<String, List<Passenger>> entry : bookingPassengers.entrySet()) {
            String pnr = entry.getKey();
            String routeId = bookingRoutes.get(pnr);
            LocalDateTime time = bookingTimes.get(pnr);
            List<Passenger> passengers = entry.getValue();

            Booking booking = new Booking(pnr, routeId, time, passengers);
            bookings.put(pnr, booking);
        }

        return bookings;
    }

    /**
     * Saves all bookings to bookings.csv.
     */
    public synchronized boolean saveBookings(Collection<Booking> bookings) {
        File file = new File(bookingsFilePath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            writer.write(BOOKINGS_HEADER);
            writer.newLine();
            for (Booking booking : bookings) {
                for (Passenger p : booking.getPassengers()) {
                    String line = String.format("%s,%s,%s,%s,%s,%d,%s,%.2f,%s",
                            escapeCsv(booking.getPnr()),
                            escapeCsv(booking.getRouteId()),
                            booking.getBookingTime().format(Booking.FORMATTER),
                            escapeCsv(p.getSeatNumber()),
                            escapeCsv(p.getName()),
                            p.getAge(),
                            escapeCsv(p.getGender()),
                            p.getFare(),
                            escapeCsv(p.getStatus()));
                    writer.write(line);
                    writer.newLine();
                }
            }
            return true;
        } catch (IOException e) {
            System.err.println("Error saving bookings: " + e.getMessage());
            return false;
        }
    }

    /**
     * Loads the waitlist queues from waitlist.csv.
     */
    public Map<String, List<WaitlistEntry>> loadWaitlist() {
        Map<String, List<WaitlistEntry>> waitlists = new LinkedHashMap<>();
        File file = new File(waitlistFilePath);

        if (!file.exists() || file.length() == 0) {
            return waitlists;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine(); // Header
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                List<String> tokens = parseCsvLine(line);
                if (tokens.size() >= 6) {
                    try {
                        String routeId = tokens.get(0).trim().toUpperCase();
                        String waitlistId = tokens.get(1).trim().toUpperCase();
                        String name = tokens.get(2).trim();
                        int age = Integer.parseInt(tokens.get(3).trim());
                        String gender = tokens.get(4).trim().toUpperCase();
                        LocalDateTime requestTime = LocalDateTime.parse(tokens.get(5).trim(), WaitlistEntry.FORMATTER);

                        WaitlistEntry entry = new WaitlistEntry(waitlistId, routeId, name, age, gender, requestTime);
                        waitlists.computeIfAbsent(routeId, k -> new ArrayList<>()).add(entry);
                    } catch (Exception ex) {
                        System.err.println("Warning: Skipping corrupted waitlist record: " + line + " (" + ex.getMessage() + ")");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading waitlist file: " + e.getMessage());
        }

        return waitlists;
    }

    /**
     * Saves all waitlist queues to waitlist.csv.
     */
    public synchronized boolean saveWaitlist(Map<String, List<WaitlistEntry>> waitlistMap) {
        File file = new File(waitlistFilePath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            writer.write(WAITLIST_HEADER);
            writer.newLine();

            if (waitlistMap != null) {
                for (Map.Entry<String, List<WaitlistEntry>> entry : waitlistMap.entrySet()) {
                    String routeId = entry.getKey();
                    for (WaitlistEntry wl : entry.getValue()) {
                        String line = String.format("%s,%s,%s,%d,%s,%s",
                                escapeCsv(routeId),
                                escapeCsv(wl.getWaitlistId()),
                                escapeCsv(wl.getPassengerName()),
                                wl.getPassengerAge(),
                                escapeCsv(wl.getPassengerGender()),
                                wl.getRequestTime().format(WaitlistEntry.FORMATTER));
                        writer.write(line);
                        writer.newLine();
                    }
                }
            }
            return true;
        } catch (IOException e) {
            System.err.println("Error saving waitlist: " + e.getMessage());
            return false;
        }
    }

    /**
     * Parses a single CSV line according to RFC-4180 rules.
     */
    public static List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        if (line == null) return tokens;

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    current.append('\"');
                    i++; // Skip escaped double quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        tokens.add(current.toString().trim());
        return tokens;
    }

    /**
     * Escapes a string for RFC-4180 CSV output.
     */
    public static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
