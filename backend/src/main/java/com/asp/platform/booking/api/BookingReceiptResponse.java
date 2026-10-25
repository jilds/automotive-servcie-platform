package com.asp.platform.booking.api;

import java.time.Instant;

import com.asp.platform.booking.application.BookingReceipt;

/// The answer to a new booking: its number and the status link's token, shown only this once.
record BookingReceiptResponse(int number, String statusToken, Instant slotStart) {

    static BookingReceiptResponse of(BookingReceipt receipt) {
        return new BookingReceiptResponse(receipt.number(), receipt.statusToken(), receipt.slotStart());
    }
}
