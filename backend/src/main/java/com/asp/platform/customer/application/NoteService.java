package com.asp.platform.customer.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.customer.domain.CustomerNotFoundException;
import com.asp.platform.customer.domain.CustomerRepository;
import com.asp.platform.customer.domain.Note;
import com.asp.platform.customer.domain.NoteRepository;
import com.asp.platform.customer.domain.VehicleNotFoundException;
import com.asp.platform.customer.domain.VehicleRepository;
import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.shared.CurrentUser;

/// Dated observations on vehicles (every role) and notes on customers (owner and front desk, checked by the
/// controller). The author's name comes from the workshop's member list.
@Service
@Transactional
public class NoteService {

    private final NoteRepository notes;
    private final VehicleRepository vehicles;
    private final CustomerRepository customers;
    private final WorkshopDirectory workshops;
    private final Clock clock;

    NoteService(NoteRepository notes, VehicleRepository vehicles, CustomerRepository customers,
            WorkshopDirectory workshops, Clock clock) {
        this.notes = notes;
        this.vehicles = vehicles;
        this.customers = customers;
        this.workshops = workshops;
        this.clock = clock;
    }

    /// A note with the name of its author (null when the user is no longer known).
    public record Entry(Note note, String authorName) {
    }

    @Transactional(readOnly = true)
    public List<Entry> vehicleObservations(CurrentUser user, UUID vehicleId) {
        vehicles.findById(vehicleId).orElseThrow(VehicleNotFoundException::new);
        return withAuthors(user, notes.vehicleObservations(vehicleId));
    }

    public Entry addVehicleObservation(CurrentUser user, UUID vehicleId, String body) {
        vehicles.findById(vehicleId).orElseThrow(VehicleNotFoundException::new);
        Note saved = notes.addVehicleObservation(
                new Note(UUID.randomUUID(), vehicleId, body, user.userId(), clock.instant()));
        return withAuthors(user, List.of(saved)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<Entry> customerNotes(CurrentUser user, UUID customerId) {
        customers.findById(customerId).orElseThrow(CustomerNotFoundException::new);
        return withAuthors(user, notes.customerNotes(customerId));
    }

    public Entry addCustomerNote(CurrentUser user, UUID customerId, String body) {
        customers.findById(customerId).orElseThrow(CustomerNotFoundException::new);
        Note saved = notes.addCustomerNote(
                new Note(UUID.randomUUID(), customerId, body, user.userId(), clock.instant()));
        return withAuthors(user, List.of(saved)).getFirst();
    }

    private List<Entry> withAuthors(CurrentUser user, List<Note> found) {
        Map<UUID, String> names = workshops.memberNames(user.tenantId());
        return found.stream().map(n -> new Entry(n, names.get(n.authorId()))).toList();
    }
}
