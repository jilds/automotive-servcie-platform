package com.asp.platform.workshop.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.application.Board;
import com.asp.platform.workshop.application.BoardCard;
import com.asp.platform.workshop.domain.WorkOrderStatus;

record BoardResponse(Map<WorkOrderStatus, List<Card>> columns) {

    record Card(UUID id, int number, @Nullable String plate, String vehicle, @Nullable UUID assignedMechanicId,
            Instant statusChangedAt) {

        static Card of(BoardCard card) {
            return new Card(card.id(), card.number(), card.plate(), card.vehicle(), card.assignedMechanicId(),
                    card.statusChangedAt());
        }
    }

    static BoardResponse of(Board board) {
        Map<WorkOrderStatus, List<Card>> columns = new LinkedHashMap<>();
        board.columns().forEach((status, cards) -> columns.put(status, cards.stream().map(Card::of).toList()));
        return new BoardResponse(columns);
    }
}
