package com.nammaroute.service;

import com.nammaroute.model.Bus;
import com.nammaroute.storage.FileStorageService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service managing bus inventory, routes, and seat map visualization.
 */
public class BusService {
    private final Map<String, Bus> buses;
    private final FileStorageService storageService;

    public BusService(FileStorageService storageService) {
        this.storageService = storageService;
        this.buses = new LinkedHashMap<>();
        loadBuses();
    }

    public void loadBuses() {
        buses.clear();
        Map<String, Bus> loaded = storageService.loadBuses();
        buses.putAll(loaded);
    }

    public boolean addBus(Bus bus) {
        if (bus == null || bus.getRouteId() == null || bus.getRouteId().trim().isEmpty()) {
            System.out.println(">> Error: Invalid bus details.");
            return false;
        }

        String routeId = bus.getRouteId().trim().toUpperCase();
        if (buses.containsKey(routeId)) {
            System.out.println(">> Error: Route ID '" + routeId + "' already exists! Duplicate Route IDs are not allowed.");
            return false;
        }

        buses.put(routeId, bus);
        boolean saved = storageService.saveBuses(buses.values());
        if (saved) {
            System.out.println(">> Success: Bus route '" + routeId + "' (" + bus.getOrigin() + " to " +
                    bus.getDestination() + ") added successfully!");
        } else {
            System.out.println(">> Warning: Bus added in memory but failed to write to storage.");
        }
        return true;
    }

    public Bus getBus(String routeId) {
        if (routeId == null) return null;
        return buses.get(routeId.trim().toUpperCase());
    }

    public Collection<Bus> getAllBuses() {
        return new ArrayList<>(buses.values());
    }

    public Map<String, Bus> getBusMap() {
        return buses;
    }

    public void displayAllBuses() {
        System.out.println();
        System.out.println("==========================================================================================================");
        System.out.println("                                      CURRENT BUS SERVICES SCHEDULE                                       ");
        System.out.println("==========================================================================================================");
        System.out.printf("%-10s %-16s %-16s %-18s %-14s %-10s %-10s %-10s%n",
                "Route ID", "Origin", "Destination", "Departure", "Type", "Fare (INR)", "Available", "Capacity");
        System.out.println("----------------------------------------------------------------------------------------------------------");

        if (buses.isEmpty()) {
            System.out.println(">> No bus services currently available.");
        } else {
            for (Bus bus : buses.values()) {
                System.out.printf("%-10s %-16s %-16s %-18s %-14s Rs. %-7.2f %-10d %-10d%n",
                        bus.getRouteId(),
                        bus.getOrigin(),
                        bus.getDestination(),
                        bus.getDepartureTime().format(Bus.DATE_TIME_FORMATTER),
                        bus.getBusType().getDisplayName(),
                        bus.getBasePrice(),
                        bus.getAvailableSeatsCount(),
                        bus.getTotalSeats());
            }
        }
        System.out.println("==========================================================================================================");
        System.out.println();
    }

    public void displaySeatMap(String routeId) {
        Bus bus = getBus(routeId);
        if (bus == null) {
            System.out.println(">> Error: No bus found with Route ID '" + routeId + "'.");
            return;
        }
        bus.displaySeatMap();
    }
}
