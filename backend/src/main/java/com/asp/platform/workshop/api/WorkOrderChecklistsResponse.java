package com.asp.platform.workshop.api;

import java.util.List;

import com.asp.platform.workshop.application.ChecklistsView;

/// The started checklists of a work order (CHECK_IN first). A kind that is not in the list was not started.
record WorkOrderChecklistsResponse(boolean deliveryChecklistRequired, List<ChecklistResponse> checklists) {

    static WorkOrderChecklistsResponse of(ChecklistsView view) {
        return new WorkOrderChecklistsResponse(view.deliveryChecklistRequired(),
                view.checklists().stream().map(ChecklistResponse::of).toList());
    }
}
