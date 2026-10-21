package com.asp.platform.customer.domain;

import com.asp.platform.shared.ConflictException;

/// An edit tried to set the odometer below the reading already on record.
public class MileageDecreaseException extends ConflictException {

    public MileageDecreaseException(int recorded, int requested) {
        super("mileage-decrease", "Mileage cannot go down from " + recorded + " to " + requested);
    }
}
