package com.asp.platform.workshop.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.asp.platform.shared.StaleDataException;

/// The pure rules of time entries, photos and checklists.
class WorkshopFloorRulesTests {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID MECHANIC = UUID.randomUUID();
    private static final UUID DESK = UUID.randomUUID();

    @Test
    void should_recognise_images_by_their_first_bytes() {
        assertThat(ImageType.sniff(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})).contains(ImageType.JPEG);
        assertThat(ImageType.sniff(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}))
                .contains(ImageType.PNG);
        assertThat(ImageType.sniff("RIFF\0\0\0\0WEBPVP8 ".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)))
                .contains(ImageType.WEBP);
        assertThat(ImageType.sniff("RIFF\0\0\0\0WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)))
                .isEmpty();
        assertThat(ImageType.sniff("GIF89a".getBytes(java.nio.charset.StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageType.sniff(new byte[0])).isEmpty();
        assertThat(ImageType.ofContentType("image/webp")).isEqualTo(ImageType.WEBP);
    }

    @Test
    void should_check_size_before_type_and_describe_the_photo() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
        WorkOrderPhoto photo = WorkOrderPhoto.accept(UUID.randomUUID(), ORDER, PhotoStage.WORK, "  Frente  ", jpeg,
                MECHANIC, NOW).photo();
        assertThat(photo.caption()).isEqualTo("Frente");
        assertThat(photo.type()).isEqualTo(ImageType.JPEG);
        assertThat(photo.sizeBytes()).isEqualTo(6);
        assertThat(photo.sha256()).hasSize(64);

        byte[] tooBig = new byte[WorkOrderPhoto.MAX_BYTES + 1];
        assertThatThrownBy(() -> WorkOrderPhoto.accept(UUID.randomUUID(), ORDER, PhotoStage.WORK, null, tooBig,
                MECHANIC, NOW)).isInstanceOf(PhotoTooLargeException.class);
        assertThatThrownBy(() -> WorkOrderPhoto.accept(UUID.randomUUID(), ORDER, PhotoStage.WORK, null,
                new byte[] {1, 2, 3}, MECHANIC, NOW)).isInstanceOf(PhotoTypeNotAllowedException.class);
        assertThatThrownBy(() -> WorkOrderPhoto.accept(UUID.randomUUID(), ORDER, PhotoStage.WORK, null,
                new byte[0], MECHANIC, NOW)).isInstanceOf(InvalidPhotoInputException.class);

        photo.describe(PhotoStage.DELIVERY, " ");
        assertThat(photo.caption()).isNull();
        assertThat(photo.stage()).isEqualTo(PhotoStage.DELIVERY);
        assertThatThrownBy(() -> photo.requireVersion(3)).isInstanceOf(StaleDataException.class);
    }

    @Test
    void should_count_a_running_entry_up_to_now_and_stop_it_once() {
        TimeEntry entry = TimeEntry.start(UUID.randomUUID(), ORDER, null, MECHANIC, NOW.minusSeconds(90));
        assertThat(entry.isRunning()).isTrue();
        assertThat(entry.seconds(NOW)).isEqualTo(90);

        entry.stop(NOW, true);
        assertThat(entry.isRunning()).isFalse();
        assertThat(entry.autoStopped()).isTrue();
        assertThat(entry.seconds(NOW.plusSeconds(1000))).isEqualTo(90);
        assertThatThrownBy(() -> entry.stop(NOW, false)).isInstanceOf(TimerNotRunningException.class);
    }

    @Test
    void should_never_give_a_negative_entry_when_the_clock_is_behind() {
        TimeEntry entry = TimeEntry.start(UUID.randomUUID(), ORDER, null, MECHANIC, NOW.plusSeconds(5));
        entry.stop(NOW, false);
        assertThat(entry.stoppedAt()).isEqualTo(entry.startedAt());
        assertThat(entry.seconds(NOW)).isZero();
    }

    @Test
    void should_correct_an_entry_only_with_a_reason_and_a_sane_range() {
        TimeEntry entry = TimeEntry.start(UUID.randomUUID(), ORDER, null, MECHANIC, NOW.minusSeconds(600));
        Instant start = NOW.minusSeconds(3600);

        assertThatThrownBy(() -> entry.correct(start, null, null, " ", DESK, NOW))
                .isInstanceOf(ReasonRequiredException.class);
        assertThatThrownBy(() -> entry.correct(NOW.plusSeconds(1), null, null, "x", DESK, NOW))
                .isInstanceOf(InvalidTimeRangeException.class);
        assertThatThrownBy(() -> entry.correct(start, NOW.plusSeconds(1), null, "x", DESK, NOW))
                .isInstanceOf(InvalidTimeRangeException.class);
        assertThatThrownBy(() -> entry.correct(start, start.minusSeconds(1), null, "x", DESK, NOW))
                .isInstanceOf(InvalidTimeRangeException.class);

        entry.correct(start, null, null, "  Comecou cedo  ", DESK, NOW);
        assertThat(entry.isRunning()).isTrue();
        assertThat(entry.startedAt()).isEqualTo(start);
        assertThat(entry.editReason()).isEqualTo("Comecou cedo");
        assertThat(entry.editedBy()).isEqualTo(DESK);
        assertThat(entry.editedAt()).isEqualTo(NOW);

        entry.correct(start, start.plusSeconds(60), null, "Parou", DESK, NOW);
        assertThat(entry.isRunning()).isFalse();
        assertThatThrownBy(() -> entry.correct(start, null, null, "x", DESK, NOW))
                .isInstanceOf(InvalidTimeRangeException.class);
    }

    @Test
    void should_copy_the_template_and_complete_when_every_required_item_is_answered() {
        ChecklistTemplate template = new ChecklistTemplate(ChecklistKind.DELIVERY,
                List.of(new TemplateItem("Chaves", true), new TemplateItem("Limpeza", false),
                        new TemplateItem("Teste", true)), true, 2);
        WorkOrderChecklist checklist = WorkOrderChecklist.start(UUID.randomUUID(), ORDER, template, DESK, NOW,
                UUID::randomUUID);

        assertThat(checklist.items()).extracting(ChecklistItem::position).containsExactly(1, 2, 3);
        assertThat(checklist.items()).extracting(ChecklistItem::label).containsExactly("Chaves", "Limpeza", "Teste");
        assertThat(checklist.requiredTotal()).isEqualTo(2);
        assertThat(checklist.isComplete()).isFalse();

        checklist.answer(checklist.items().get(1).id(), ChecklistResult.OK, null, DESK, NOW);
        assertThat(checklist.isComplete()).isFalse();
        checklist.answer(checklist.items().get(0).id(), ChecklistResult.ATTENTION, " perdida ", DESK, NOW);
        checklist.answer(checklist.items().get(2).id(), ChecklistResult.N_A, null, MECHANIC, NOW);
        assertThat(checklist.isComplete()).isTrue();
        assertThat(checklist.items().get(0).note()).isEqualTo("perdida");

        assertThatThrownBy(() -> checklist.answer(UUID.randomUUID(), ChecklistResult.OK, null, DESK, NOW))
                .isInstanceOf(ChecklistItemNotFoundException.class);
        assertThatThrownBy(() -> checklist.answer(checklist.items().get(0).id(), ChecklistResult.OK,
                "n".repeat(301), DESK, NOW)).isInstanceOf(InvalidChecklistNoteException.class);
    }

    @Test
    void should_keep_templates_between_one_and_thirty_items_and_check_the_version() {
        assertThatThrownBy(() -> new ChecklistTemplate(ChecklistKind.CHECK_IN, List.of(), true, 0))
                .isInstanceOf(InvalidChecklistItemException.class);
        assertThatThrownBy(() -> new TemplateItem(" ", true)).isInstanceOf(InvalidChecklistItemException.class);
        ChecklistTemplate template = DefaultChecklists.of(ChecklistKind.CHECK_IN, "PT");
        assertThatThrownBy(() -> template.replaceItems(List.of(new TemplateItem("x", true)), 1))
                .isInstanceOf(StaleDataException.class);
        ChecklistTemplate saved = template.replaceItems(List.of(new TemplateItem("x", true)), 0);
        assertThat(saved.custom()).isTrue();
        assertThat(saved.hasRequiredItems()).isTrue();
    }

    @Test
    void should_have_defaults_in_the_language_of_the_country() {
        for (ChecklistKind kind : ChecklistKind.values()) {
            ChecklistTemplate pt = DefaultChecklists.of(kind, "PT");
            ChecklistTemplate br = DefaultChecklists.of(kind, "BR");
            ChecklistTemplate en = DefaultChecklists.of(kind, "ES");
            assertThat(pt.custom()).isFalse();
            assertThat(pt.version()).isZero();
            assertThat(pt.items()).hasSize(6);
            assertThat(pt.items()).isNotEqualTo(br.items()).isNotEqualTo(en.items());
            assertThat(pt.hasRequiredItems()).isTrue();
        }
        assertThat(DefaultChecklists.of(ChecklistKind.CHECK_IN, "ES").items().getFirst().label())
                .isEqualTo("Vehicle documents received");
    }
}
