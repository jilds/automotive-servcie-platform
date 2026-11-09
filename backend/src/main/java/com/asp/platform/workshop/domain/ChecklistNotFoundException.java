package com.asp.platform.workshop.domain;

import com.asp.platform.shared.NotFoundException;

/// The work order has no checklist of that kind yet. 404 `checklist-not-found`.
public class ChecklistNotFoundException extends NotFoundException {

    public ChecklistNotFoundException() {
        super("checklist-not-found", "This checklist was not started");
    }
}
