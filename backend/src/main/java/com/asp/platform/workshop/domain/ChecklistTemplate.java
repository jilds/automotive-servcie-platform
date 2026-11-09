package com.asp.platform.workshop.domain;

import java.util.List;

import com.asp.platform.shared.StaleDataException;

/// The items a workshop checks at check-in or at delivery. One template per kind per workshop; until the owner saves
/// one, the built-in default is used (`custom` false, version 0).
///
/// @param custom true when the owner saved it
public record ChecklistTemplate(ChecklistKind kind, List<TemplateItem> items, boolean custom, long version) {

    public static final int MIN_ITEMS = 1;
    public static final int MAX_ITEMS = 30;
    public static final int MAX_LABEL = 120;

    public ChecklistTemplate {
        if (items.size() < MIN_ITEMS || items.size() > MAX_ITEMS) {
            throw new InvalidChecklistItemException("A checklist has " + MIN_ITEMS + " to " + MAX_ITEMS + " items");
        }
        items = List.copyOf(items);
    }

    /// The same template with other items (a save by the owner), checked against the version the owner read.
    ///
    /// @throws StaleDataException when the stored version is another
    public ChecklistTemplate replaceItems(List<TemplateItem> newItems, long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
        return new ChecklistTemplate(kind, newItems, true, version);
    }

    public boolean hasRequiredItems() {
        return items.stream().anyMatch(TemplateItem::required);
    }
}
