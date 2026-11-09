package com.asp.platform.workshop.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.shared.CurrentUser;
import com.asp.platform.workshop.domain.ChecklistIncompleteException;
import com.asp.platform.workshop.domain.ChecklistKind;
import com.asp.platform.workshop.domain.ChecklistNotFoundException;
import com.asp.platform.workshop.domain.ChecklistResult;
import com.asp.platform.workshop.domain.ChecklistTemplate;
import com.asp.platform.workshop.domain.ChecklistTemplateRepository;
import com.asp.platform.workshop.domain.DefaultChecklists;
import com.asp.platform.workshop.domain.TemplateItem;
import com.asp.platform.workshop.domain.WorkOrder;
import com.asp.platform.workshop.domain.WorkOrderChecklist;
import com.asp.platform.workshop.domain.WorkOrderChecklistRepository;
import com.asp.platform.workshop.domain.WorkOrderLockedException;
import com.asp.platform.workshop.domain.WorkOrderNotFoundException;
import com.asp.platform.workshop.domain.WorkOrderRepository;
import com.asp.platform.workshop.domain.WorkOrderStatus;

/// The workshop's checklist templates and the checklists of its work orders (copied from them when started), and the
/// delivery gate. Changes to a work order's checklist lock the order row ([WorkOrderRepository#lockById], 3 s then 409
/// `try-again`), as line changes and moves do.
@Service
public class ChecklistService {

    private final WorkOrderRepository orders;
    private final ChecklistTemplateRepository templates;
    private final WorkOrderChecklistRepository checklists;
    private final WorkshopDirectory directory;
    private final Clock clock;

    ChecklistService(WorkOrderRepository orders, ChecklistTemplateRepository templates,
            WorkOrderChecklistRepository checklists, WorkshopDirectory directory, Clock clock) {
        this.orders = orders;
        this.templates = templates;
        this.checklists = checklists;
        this.directory = directory;
        this.clock = clock;
    }

    /// Both templates (CHECK_IN first): the saved one, or the built-in default for the workshop's country.
    @Transactional(readOnly = true)
    public List<ChecklistTemplate> templates(UUID tenantId) {
        return List.of(template(tenantId, ChecklistKind.CHECK_IN), template(tenantId, ChecklistKind.DELIVERY));
    }

    /// Replaces the items of a template.
    ///
    /// @param expectedVersion the version the owner read (0 for a template never saved)
    /// @throws com.asp.platform.shared.StaleDataException                          for another stored version
    /// @throws com.asp.platform.workshop.domain.InvalidChecklistItemException      for 0 or more than 30 items, or a
    ///                                                                             label of 0 or more than 120
    ///                                                                             characters
    @Transactional
    public ChecklistTemplate saveTemplate(UUID tenantId, ChecklistKind kind, List<TemplateItem> items,
            long expectedVersion) {
        ChecklistTemplate current = template(tenantId, kind);
        return templates.save(current.replaceItems(items, expectedVersion));
    }

    /// The started checklists of the order.
    ///
    /// @throws WorkOrderNotFoundException for an unknown order
    @Transactional(readOnly = true)
    public ChecklistsView forOrder(CurrentUser user, UUID workOrderId) {
        orders.findById(workOrderId).orElseThrow(WorkOrderNotFoundException::new);
        Map<UUID, String> names = directory.memberNames(user.tenantId());
        List<ChecklistView> started = checklists.findByWorkOrder(workOrderId).stream()
                .map(checklist -> new ChecklistView(checklist, names)).toList();
        return new ChecklistsView(directory.deliveryChecklistRequired(user.tenantId()), started);
    }

    /// Starts the checklist of this kind by copying the template, or returns the one already started.
    ///
    /// @throws AccessDeniedException   for a mechanic who is not assigned
    /// @throws WorkOrderLockedException when the order is DELIVERED or CANCELLED
    @Transactional
    public StartedChecklist start(CurrentUser user, UUID workOrderId, ChecklistKind kind) {
        WorkOrder order = lockedOrderToEdit(user, workOrderId);
        Map<UUID, String> names = directory.memberNames(user.tenantId());
        Optional<WorkOrderChecklist> existing = checklists.find(workOrderId, kind);
        if (existing.isPresent()) {
            return new StartedChecklist(new ChecklistView(existing.get(), names), false);
        }
        WorkOrderChecklist started = WorkOrderChecklist.start(UUID.randomUUID(), order.id(),
                template(user.tenantId(), kind), user.userId(), clock.instant(), UUID::randomUUID);
        return new StartedChecklist(new ChecklistView(checklists.saveNew(started), names), true);
    }

    /// Gives or changes the answer of one item and returns the whole checklist.
    ///
    /// @throws AccessDeniedException                                                    for a mechanic who is not
    ///                                                                                  assigned
    /// @throws WorkOrderLockedException                                                 when the order is DELIVERED or
    ///                                                                                  CANCELLED
    /// @throws ChecklistNotFoundException                                               when it was not started
    /// @throws com.asp.platform.workshop.domain.ChecklistItemNotFoundException         for an item of another checklist
    @Transactional
    public ChecklistView answer(CurrentUser user, UUID workOrderId, ChecklistKind kind, UUID itemId,
            ChecklistResult result, @Nullable String note) {
        lockedOrderToEdit(user, workOrderId);
        WorkOrderChecklist checklist = checklists.find(workOrderId, kind).orElseThrow(ChecklistNotFoundException::new);
        checklists.saveAnswer(checklist.answer(itemId, result, note, user.userId(), clock.instant()));
        return new ChecklistView(checklist, directory.memberNames(user.tenantId()));
    }

    /// The delivery gate: with the workshop setting on, every required item of the order's delivery checklist needs an
    /// answer. A checklist not started counts every required item of the template as open. Called by the move to
    /// DELIVERED, which holds the order's lock.
    ///
    /// @throws ChecklistIncompleteException when required items are open
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireDeliveryReady(UUID tenantId, UUID workOrderId) {
        if (!directory.deliveryChecklistRequired(tenantId)) {
            return;
        }
        Optional<WorkOrderChecklist> started = checklists.find(workOrderId, ChecklistKind.DELIVERY);
        int open = started.isPresent()
                ? started.get().requiredTotal() - started.get().requiredAnswered()
                : (int) template(tenantId, ChecklistKind.DELIVERY).items().stream().filter(TemplateItem::required)
                        .count();
        if (open > 0) {
            throw new ChecklistIncompleteException(open);
        }
    }

    private ChecklistTemplate template(UUID tenantId, ChecklistKind kind) {
        return templates.find(kind).orElseGet(() -> DefaultChecklists.of(kind, directory.country(tenantId)));
    }

    private WorkOrder lockedOrderToEdit(CurrentUser user, UUID workOrderId) {
        WorkOrder order = orders.lockById(workOrderId).orElseThrow(WorkOrderNotFoundException::new);
        if (user.isMechanic() && !order.isAssignedTo(user.userId())) {
            throw new AccessDeniedException("Only the assigned mechanic can fill in a checklist");
        }
        if (order.status() == WorkOrderStatus.DELIVERED || order.status() == WorkOrderStatus.CANCELLED) {
            throw new WorkOrderLockedException(order.status());
        }
        return order;
    }
}
