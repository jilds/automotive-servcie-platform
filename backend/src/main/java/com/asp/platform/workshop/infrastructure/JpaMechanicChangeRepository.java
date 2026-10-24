package com.asp.platform.workshop.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.asp.platform.workshop.domain.MechanicChange;
import com.asp.platform.workshop.domain.MechanicChangeRepository;

@Repository
class JpaMechanicChangeRepository implements MechanicChangeRepository {

    private final SpringDataMechanicChangeRepository jpa;

    JpaMechanicChangeRepository(SpringDataMechanicChangeRepository jpa) {
        this.jpa = jpa;
    }

    /// New rows only: changes are never edited, so this is a plain persist.
    @Override
    public MechanicChange save(MechanicChange change) {
        return jpa.save(new MechanicChangeEntity(change)).toDomain();
    }

    @Override
    public List<MechanicChange> findByWorkOrder(UUID workOrderId) {
        return jpa.findByWorkOrderIdOrderByChangedAtAscIdAsc(workOrderId).stream()
                .map(MechanicChangeEntity::toDomain)
                .toList();
    }
}
