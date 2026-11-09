package com.asp.platform.workshop.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.asp.platform.workshop.domain.ChecklistTemplate;
import com.asp.platform.workshop.domain.TemplateItem;

/// `PUT /checklist-templates/{kind}`: all items, in order. `version` is the one the owner read (0 for a template never
/// saved); another stored version answers 409 `stale-data`.
record ChecklistTemplateRequest(
        @NotNull @Size(min = ChecklistTemplate.MIN_ITEMS, max = ChecklistTemplate.MAX_ITEMS) List<@Valid @NotNull Item> items,
        @NotNull Long version) {

    record Item(@NotBlank @Size(max = ChecklistTemplate.MAX_LABEL) String label, @NotNull Boolean required) {
    }

    List<TemplateItem> toItems() {
        return items.stream().map(i -> new TemplateItem(i.label(), i.required())).toList();
    }
}
