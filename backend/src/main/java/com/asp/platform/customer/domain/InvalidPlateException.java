package com.asp.platform.customer.domain;

/// A plate does not fit the format of the country the vehicle is registered in. Mapped to 400 with a field
/// error on `plate`.
public class InvalidPlateException extends RuntimeException {

    public static final String CODE = "invalid-plate";

    private final String country;

    public InvalidPlateException(String country, String plate) {
        super("Plate " + plate + " is not a valid " + country + " plate");
        this.country = country;
    }

    public String country() {
        return country;
    }
}
