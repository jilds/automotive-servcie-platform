package com.asp.platform.workshop.domain;

import com.asp.platform.shared.InvalidInputException;

/// The front desk records a customer's decision without a note on how the customer gave it. 400
/// `decision-note-required` on the field `note`.
public class DecisionNoteRequiredException extends InvalidInputException {

    public DecisionNoteRequiredException() {
        super("decision-note-required", "note", "Say how the customer decided");
    }
}
