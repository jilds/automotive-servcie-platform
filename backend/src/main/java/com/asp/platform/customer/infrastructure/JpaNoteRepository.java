package com.asp.platform.customer.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.asp.platform.customer.domain.Note;
import com.asp.platform.customer.domain.NoteRepository;

/// New rows only, never edited: plain persists (no `Upserts`).
@Repository
class JpaNoteRepository implements NoteRepository {

    private final SpringDataVehicleObservationRepository observations;
    private final SpringDataCustomerNoteRepository notes;

    JpaNoteRepository(SpringDataVehicleObservationRepository observations, SpringDataCustomerNoteRepository notes) {
        this.observations = observations;
        this.notes = notes;
    }

    @Override
    public Note addVehicleObservation(Note note) {
        return observations.save(new VehicleObservationEntity(note)).toDomain();
    }

    @Override
    public List<Note> vehicleObservations(UUID vehicleId) {
        return observations.findByVehicleIdOrderByCreatedAtDescIdDesc(vehicleId).stream()
                .map(VehicleObservationEntity::toDomain).toList();
    }

    @Override
    public Note addCustomerNote(Note note) {
        return notes.save(new CustomerNoteEntity(note)).toDomain();
    }

    @Override
    public List<Note> customerNotes(UUID customerId) {
        return notes.findByCustomerIdOrderByCreatedAtDescIdDesc(customerId).stream()
                .map(CustomerNoteEntity::toDomain).toList();
    }
}
