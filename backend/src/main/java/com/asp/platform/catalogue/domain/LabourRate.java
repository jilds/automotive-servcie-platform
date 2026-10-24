package com.asp.platform.catalogue.domain;

/// The signed-in workshop's labour rate, which prices the services charged by the hour.
public interface LabourRate {

    /// The hourly labour rate before VAT, in cents, as set in the workshop settings now.
    long hourlyCents();
}
