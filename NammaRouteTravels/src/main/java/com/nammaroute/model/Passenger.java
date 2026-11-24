package com.nammaroute.model;

/**
 * Represents a passenger associated with a specific booked seat.
 */
public class Passenger {
    private String name;
    private int age;
    private String gender; // "M", "F", "Other"
    private String seatNumber; // Normalized seat number e.g. "1A"
    private double fare;
    private String status; // "CONFIRMED" or "CANCELLED"

    public Passenger(String name, int age, String gender, String seatNumber, double fare) {
        this(name, age, gender, seatNumber, fare, "CONFIRMED");
    }

    public Passenger(String name, int age, String gender, String seatNumber, double fare, String status) {
        this.name = name != null ? name.trim() : "";
        this.age = age;
        this.gender = gender != null ? gender.trim() : "Other";
        this.seatNumber = Seat.normalize(seatNumber);
        this.fare = fare;
        this.status = status != null ? status.trim().toUpperCase() : "CONFIRMED";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = Seat.normalize(seatNumber);
    }

    public double getFare() {
        return fare;
    }

    public void setFare(double fare) {
        this.fare = fare;
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
        return String.format("%s (Seat: %s, Age: %d, Gender: %s, Fare: Rs. %.2f, Status: %s)",
                name, seatNumber, age, gender, fare, status);
    }
}
