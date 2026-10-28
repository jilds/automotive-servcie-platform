package com.asp.platform.workshop.api;

import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.application.Board;
import com.asp.platform.workshop.application.BoardCard;
import com.asp.platform.workshop.application.VehiclePlate;
import com.asp.platform.workshop.domain.QuoteStatus;
import com.asp.platform.workshop.domain.WorkOrderStatus;

record BoardResponse(Map<WorkOrderStatus, List<Card>> columns) {

    /// The plate fields are null when the vehicle is not found. `quoteStatus` is the latest quote's status, or null.
    record Card(UUID id, int number, @Nullable String plate, @Nullable String plateDisplay,
            @Nullable String plateEra, @Nullable String registrationCountry, @Nullable YearMonth registrationDate,
            String vehicle, @Nullable UUID assignedMechanicId, Instant statusChangedAt,
            @Nullable QuoteStatus quoteStatus) {

        static Card of(BoardCard card) {
            VehiclePlate plate = card.plate();
            return new Card(card.id(), card.number(),
                    plate == null ? null : plate.plate(),
                    plate == null ? null : plate.display(),
                    plate == null ? null : plate.era(),
                    plate == null ? null : plate.registrationCountry(),
                    plate == null ? null : plate.registrationDate(),
                    card.vehicle(), card.assignedMechanicId(), card.statusChangedAt(), card.quoteStatus());
        }
    }

    static BoardResponse of(Board board) {
        Map<WorkOrderStatus, List<Card>> columns = new LinkedHashMap<>();
        board.columns().forEach((status, cards) -> columns.put(status, cards.stream().map(Card::of).toList()));
        return new BoardResponse(columns);
    }
}
