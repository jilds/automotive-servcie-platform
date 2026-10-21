package com.asp.platform.customer.domain;

/// The numbering series a plate belongs to, which tells roughly when the vehicle was first registered.
/// Constants are prefixed with the country they belong to; a new country adds its own constants.
public enum PlateEra {

    /// Portugal since March 2020: `AA-00-AA`.
    PT_2020,
    /// Portugal 2005 to 2020: `00-AA-00`.
    PT_2005,
    /// Portugal 1992 to 2005: `00-00-AA`.
    PT_1992,
    /// Portugal 1937 to 1992: `AA-00-00`; classic cars may still carry it.
    PT_1937
}
