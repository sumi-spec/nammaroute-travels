package com.nammaroute.util;

import com.nammaroute.model.Bus;
import com.nammaroute.model.BusType;
import com.nammaroute.model.Seat;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * Utility class providing robust, foolproof validation for user inputs.
 */
public class InputValidator {

    public static String readNonEmptyString(Scanner scanner, String prompt, String fieldName) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine();
            if (line != null && !line.trim().isEmpty()) {
                return line.trim();
            }
            System.out.println(">> Error: " + fieldName + " cannot be empty. Please try again.");
        }
    }

    public static int readIntInRange(Scanner scanner, String prompt, int min, int max, String fieldName) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine();
            if (line != null) {
                try {
                    int val = Integer.parseInt(line.trim());
                    if (val >= min && val <= max) {
                        return val;
                    }
                    System.out.printf(">> Error: %s must be between %d and %d.%n", fieldName, min, max);
                } catch (NumberFormatException e) {
                    System.out.println(">> Error: Invalid numeric input. Please enter a valid number.");
                }
            }
        }
    }

    public static double readPositiveDouble(Scanner scanner, String prompt, String fieldName) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine();
            if (line != null) {
                try {
                    double val = Double.parseDouble(line.trim());
                    if (val > 0.0) {
                        return val;
                    }
                    System.out.println(">> Error: " + fieldName + " must be greater than zero.");
                } catch (NumberFormatException e) {
                    System.out.println(">> Error: Invalid number format. Please enter a valid decimal number.");
                }
            }
        }
    }

    public static LocalDateTime readDateTime(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine();
            if (line != null && !line.trim().isEmpty()) {
                try {
                    return LocalDateTime.parse(line.trim(), Bus.DATE_TIME_FORMATTER);
                } catch (DateTimeParseException e) {
                    System.out.println(">> Error: Invalid date format. Please enter in 'yyyy-MM-dd HH:mm' format (e.g. 2026-11-27 21:30).");
                }
            } else {
                System.out.println(">> Error: Date and time cannot be empty.");
            }
        }
    }

    public static BusType readBusType(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine();
            if (line != null) {
                BusType type = BusType.fromString(line.trim());
                if (type != null) {
                    return type;
                }
            }
            System.out.println(">> Error: Invalid bus type. Enter '1' for SEATER (2x2) or '2' for SLEEPER (2x1).");
        }
    }

    public static String readGender(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine();
            if (line != null) {
                String trimmed = line.trim().toUpperCase();
                if (trimmed.equals("M") || trimmed.equals("MALE")) {
                    return "M";
                } else if (trimmed.equals("F") || trimmed.equals("FEMALE")) {
                    return "F";
                } else if (trimmed.equals("OTHER") || trimmed.equals("O")) {
                    return "Other";
                }
            }
            System.out.println(">> Error: Invalid gender. Please enter 'M', 'F', or 'Other'.");
        }
    }

    /**
     * Parses comma or space separated seat numbers, returning normalized unique seat strings.
     */
    public static List<String> parseSeatInput(String input) {
        List<String> seats = new ArrayList<>();
        if (input == null || input.trim().isEmpty()) return seats;

        // Split by commas, spaces, or tabs
        String[] parts = input.split("[,\\s]+");
        for (String part : parts) {
            if (!part.trim().isEmpty()) {
                seats.add(Seat.normalize(part));
            }
        }
        return seats;
    }

    /**
     * Validates that all requested seats are valid and available on the bus.
     * Returns an error message if invalid, or null if all seats are valid.
     */
    public static String validateSeatsForBooking(List<String> requestedSeats, Bus bus) {
        if (requestedSeats == null || requestedSeats.isEmpty()) {
            return "No seats were entered.";
        }

        // Check for duplicates in the request itself
        Set<String> uniqueCheck = new HashSet<>();
        for (String seatNum : requestedSeats) {
            if (!uniqueCheck.add(seatNum)) {
                return "Duplicate seat '" + seatNum + "' requested in the same booking.";
            }
        }

        // Check each seat on the bus
        for (String seatNum : requestedSeats) {
            Seat seat = bus.getSeat(seatNum);
            if (seat == null) {
                return "Seat '" + seatNum + "' does not exist on bus route " + bus.getRouteId() + ".";
            }
            if (seat.isBooked()) {
                return "Seat '" + seat.getDisplayLabel() + "' is already booked. Please choose an available seat.";
            }
        }

        return null; // All valid
    }
}
