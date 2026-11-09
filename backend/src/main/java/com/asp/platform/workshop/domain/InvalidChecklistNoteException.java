package com.asp.platform.workshop.domain;

import com.asp.platform.shared.InvalidInputException;

/// A checklist note over 300 characters. 400 `validation` on the field `note`.
public class InvalidChecklistNoteException extends InvalidInputException {

    public InvalidChecklistNoteException() {
        super("validation", "note", "A note has at most " + WorkOrderChecklist.MAX_NOTE + " characters");
    }
}
