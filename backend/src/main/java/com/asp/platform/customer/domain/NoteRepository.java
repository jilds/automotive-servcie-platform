package com.asp.platform.customer.domain;

import java.util.List;
import java.util.UUID;

/// Row-level security limits every query to the current workshop. Newest first; rows are only added.
public interface NoteRepository {

    Note addVehicleObservation(Note note);

    List<Note> vehicleObservations(UUID vehicleId);

    Note addCustomerNote(Note note);

    List<Note> customerNotes(UUID customerId);
}
