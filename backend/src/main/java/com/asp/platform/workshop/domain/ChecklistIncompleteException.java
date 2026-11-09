package com.asp.platform.workshop.domain;

import com.asp.platform.shared.ConflictException;

/// The work order cannot be delivered while required items of its delivery checklist are open. 409
/// `checklist-incomplete`.
public class ChecklistIncompleteException extends ConflictException {

    public ChecklistIncompleteException(int open) {
        super("checklist-incomplete", open + " required item(s) of the delivery checklist are still open");
    }
}
