package com.asp.platform.workshop.domain;

import java.util.Optional;

/// The templates the owner saved; row-level security limits every query to the current workshop.
public interface ChecklistTemplateRepository {

    /// Empty until the owner saves the template of that kind.
    Optional<ChecklistTemplate> find(ChecklistKind kind);

    /// Inserts the first save (the version must be 0) or replaces the items of the stored template.
    ChecklistTemplate save(ChecklistTemplate template);
}
