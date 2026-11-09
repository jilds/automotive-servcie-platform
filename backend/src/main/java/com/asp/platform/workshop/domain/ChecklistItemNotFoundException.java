package com.asp.platform.workshop.domain;

import com.asp.platform.shared.NotFoundException;

/// The item is not in that checklist. 404 `checklist-item-not-found`.
public class ChecklistItemNotFoundException extends NotFoundException {

    public ChecklistItemNotFoundException() {
        super("checklist-item-not-found", "Checklist item not found");
    }
}
