package com.asp.platform.workshop.domain;

/// One line of a checklist template.
///
/// @param label    what to check, 1 to [ChecklistTemplate#MAX_LABEL] characters
/// @param required whether it must be answered before delivery (when the workshop requires the delivery checklist)
public record TemplateItem(String label, boolean required) {

    public TemplateItem {
        label = label.strip();
        if (label.isEmpty() || label.length() > ChecklistTemplate.MAX_LABEL) {
            throw new InvalidChecklistItemException("A checklist item has 1 to " + ChecklistTemplate.MAX_LABEL
                    + " characters");
        }
    }
}
