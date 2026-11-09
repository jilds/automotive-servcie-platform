package com.asp.platform.workshop.domain;

import com.asp.platform.shared.InvalidInputException;

/// A checklist template with no item, too many, or an item label that is empty or too long. 400 `validation` on the
/// field `items`.
public class InvalidChecklistItemException extends InvalidInputException {

    public InvalidChecklistItemException(String message) {
        super("validation", "items", message);
    }
}
