package com.asp.platform.workshop.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.shared.CurrentUser;
import com.asp.platform.workshop.domain.PhotoLimitReachedException;
import com.asp.platform.workshop.domain.PhotoNotFoundException;
import com.asp.platform.workshop.domain.PhotoRepository;
import com.asp.platform.workshop.domain.PhotoStage;
import com.asp.platform.workshop.domain.PhotoStorageFullException;
import com.asp.platform.workshop.domain.PhotoStore;
import com.asp.platform.workshop.domain.WorkOrder;
import com.asp.platform.workshop.domain.WorkOrderNotFoundException;
import com.asp.platform.workshop.domain.WorkOrderPhoto;
import com.asp.platform.workshop.domain.WorkOrderRepository;
import com.asp.platform.workshop.domain.WorkOrderStatus;

/// Photos of a work order: the metadata in [PhotoRepository], the bytes in [PhotoStore]. Uploads lock the work order
/// row ([WorkOrderRepository#lockById], 3 s then 409 `try-again`) so two uploads cannot pass the limit of 30 photos
/// per order together, and take a per-workshop advisory lock before summing the stored bytes, so two uploads on
/// different orders cannot pass the storage quota together. Changing and deleting a photo lock the order row too.
///
/// Who may do what: everyone reads, except that photos of the DOCUMENTS stage exist only for the owner and the front
/// desk (a mechanic neither lists nor reads them, and gets 404 on them). The owner, the front desk and the order's
/// assigned mechanic upload. Changing a photo, and deleting one (a soft delete with a reason) before the order is
/// delivered or cancelled, is for the owner, the front desk, and the mechanic who uploaded it while still assigned;
/// afterwards only the owner may delete.
@Service
public class PhotoService {

    private final WorkOrderRepository orders;
    private final PhotoRepository photos;
    private final PhotoStore store;
    private final WorkshopDirectory directory;
    private final PhotoProperties properties;
    private final Clock clock;

    PhotoService(WorkOrderRepository orders, PhotoRepository photos, PhotoStore store, WorkshopDirectory directory,
            PhotoProperties properties, Clock clock) {
        this.orders = orders;
        this.photos = photos;
        this.store = store;
        this.directory = directory;
        this.properties = properties;
        this.clock = clock;
    }

    /// @throws AccessDeniedException                                              for a mechanic who is not assigned,
    ///                                                                            or who uploads to DOCUMENTS
    /// @throws PhotoLimitReachedException                                         when the order has 30 photos
    /// @throws PhotoStorageFullException                                          when the workshop's quota is used up
    /// @throws com.asp.platform.workshop.domain.PhotoTooLargeException            over 5 MB
    /// @throws com.asp.platform.workshop.domain.PhotoTypeNotAllowedException      for bytes that are not JPEG, PNG or
    ///                                                                            WebP
    @Transactional
    public PhotoView upload(CurrentUser user, UUID workOrderId, PhotoStage stage, @Nullable String caption,
            byte[] bytes) {
        WorkOrder order = orders.lockById(workOrderId).orElseThrow(WorkOrderNotFoundException::new);
        if (user.isMechanic() && (!order.isAssignedTo(user.userId()) || stage.isRestricted())) {
            throw new AccessDeniedException("Only the assigned mechanic can add photos, and not documents");
        }
        WorkOrderPhoto.Accepted accepted = WorkOrderPhoto.accept(UUID.randomUUID(), workOrderId, stage, caption,
                bytes, user.userId(), clock.instant());
        if (photos.countByWorkOrder(workOrderId) >= WorkOrderPhoto.MAX_PER_ORDER) {
            throw new PhotoLimitReachedException();
        }
        photos.lockStorageQuota(user.tenantId());
        if (photos.storedBytes() + accepted.bytes().length > properties.maxBytesPerWorkshop()) {
            throw new PhotoStorageFullException();
        }
        WorkOrderPhoto saved = photos.save(accepted.photo());
        store.put(saved.id(), accepted.bytes());
        return view(saved, directory.memberNames(user.tenantId()));
    }

    /// Oldest first, without deleted photos and, for a mechanic, without DOCUMENTS.
    @Transactional(readOnly = true)
    public List<PhotoView> list(CurrentUser user, UUID workOrderId) {
        orders.findById(workOrderId).orElseThrow(WorkOrderNotFoundException::new);
        Map<UUID, String> names = directory.memberNames(user.tenantId());
        return photos.findByWorkOrder(workOrderId).stream()
                .filter(photo -> canSee(user, photo))
                .map(photo -> view(photo, names)).toList();
    }

    /// The metadata of one photo of the order (for the headers of the content answer).
    ///
    /// @throws PhotoNotFoundException for a photo of another order or workshop, a deleted one, or a DOCUMENTS photo
    ///                                asked for by a mechanic
    @Transactional(readOnly = true)
    public WorkOrderPhoto find(CurrentUser user, UUID workOrderId, UUID photoId) {
        return photos.findById(photoId)
                .filter(p -> p.workOrderId().equals(workOrderId) && canSee(user, p))
                .orElseThrow(PhotoNotFoundException::new);
    }

    /// The bytes of a photo [#find] returned.
    ///
    /// @throws PhotoNotFoundException when the photo was deleted meanwhile
    @Transactional(readOnly = true)
    public byte[] bytes(WorkOrderPhoto photo) {
        return store.get(photo.id()).orElseThrow(PhotoNotFoundException::new);
    }

    /// Changes the stage and the caption.
    ///
    /// @throws AccessDeniedException                      for a mechanic who did not upload it or is not assigned, or
    ///                                                    who moves it to DOCUMENTS
    /// @throws com.asp.platform.shared.StaleDataException for another stored version
    @Transactional
    public PhotoView update(CurrentUser user, UUID workOrderId, UUID photoId, PhotoStage stage,
            @Nullable String caption, long expectedVersion) {
        WorkOrder order = orders.lockById(workOrderId).orElseThrow(WorkOrderNotFoundException::new);
        WorkOrderPhoto photo = find(user, workOrderId, photoId);

        requireMayChange(user, order, photo);
        if (user.isMechanic() && stage.isRestricted()) {
            throw new AccessDeniedException("Mechanics cannot move a photo to documents");
        }
        photo.requireVersion(expectedVersion);
        photo.describe(stage, caption);
        return view(photos.save(photo), directory.memberNames(user.tenantId()));
    }

    /// Deletes the photo for everyone (soft delete): who, when and why are kept, the bytes go after the retention
    /// period. Once the order is DELIVERED or CANCELLED only the owner may delete.
    ///
    /// @throws AccessDeniedException                                       as above
    /// @throws com.asp.platform.workshop.domain.ReasonRequiredException    without a reason
    @Transactional
    public void delete(CurrentUser user, UUID workOrderId, UUID photoId, @Nullable String reason) {
        WorkOrder order = orders.lockById(workOrderId).orElseThrow(WorkOrderNotFoundException::new);
        WorkOrderPhoto photo = find(user, workOrderId, photoId);

        requireMayChange(user, order, photo);
        boolean closed = order.status() == WorkOrderStatus.DELIVERED || order.status() == WorkOrderStatus.CANCELLED;
        if (closed && !user.isOwner()) {
            throw new AccessDeniedException("Only the owner deletes photos of a delivered or cancelled work order");
        }
        photo.markDeleted(user.userId(), reason, clock.instant());
        photos.save(photo);
    }

    /// Ids of the current workshop's deleted photos whose retention period is over and whose bytes are still stored.
    /// Run it inside the workshop's [com.asp.platform.shared.TenantScope] (see [PhotoPurge]).
    @Transactional(readOnly = true)
    public List<UUID> purgeDueIds(Instant now, int limit) {
        return photos.findPurgeDueIds(now.minus(Duration.ofDays(properties.deletedRetentionDays())), limit);
    }

    /// Drops the bytes of one deleted photo, in its own transaction; the metadata row stays as the audit.
    ///
    /// @return false when the photo is gone, not deleted, or already purged
    @Transactional
    public boolean purge(UUID photoId, Instant now) {
        WorkOrderPhoto photo = photos.findById(photoId).orElse(null);
        if (photo == null || !photo.isDeleted() || photo.purgedAt() != null) {
            return false;
        }
        store.delete(photoId);
        photo.markPurged(now);
        photos.save(photo);
        return true;
    }

    private static boolean canSee(CurrentUser user, WorkOrderPhoto photo) {
        return !photo.isDeleted() && !(user.isMechanic() && photo.stage().isRestricted());
    }

    private static void requireMayChange(CurrentUser user, WorkOrder order, WorkOrderPhoto photo) {
        if (user.isMechanic()
                && (!photo.uploadedBy().equals(user.userId()) || !order.isAssignedTo(user.userId()))) {
            throw new AccessDeniedException("A mechanic can only change the photos they added to their own jobs");
        }
    }

    private static PhotoView view(WorkOrderPhoto photo, Map<UUID, String> names) {
        return new PhotoView(photo, names.get(photo.uploadedBy()));
    }
}
