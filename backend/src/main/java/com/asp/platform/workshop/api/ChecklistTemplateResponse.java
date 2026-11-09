package com.asp.platform.workshop.api;

import java.util.List;

import com.asp.platform.workshop.domain.ChecklistKind;
import com.asp.platform.workshop.domain.ChecklistTemplate;

/// A checklist template: `custom` false means the built-in default (version 0), not saved by the owner yet.
record ChecklistTemplateResponse(ChecklistKind kind, boolean custom, long version, List<Item> items) {

    record Item(String label, boolean required) {
    }

    static ChecklistTemplateResponse of(ChecklistTemplate template) {
        return new ChecklistTemplateResponse(template.kind(), template.custom(), template.version(),
                template.items().stream().map(i -> new Item(i.label(), i.required())).toList());
    }
}
