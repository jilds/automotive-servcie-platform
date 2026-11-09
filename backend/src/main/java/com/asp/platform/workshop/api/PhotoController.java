package com.asp.platform.workshop.api;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import com.asp.platform.shared.CurrentUser;
import com.asp.platform.workshop.application.PhotoService;
import com.asp.platform.workshop.domain.PhotoStage;
import com.asp.platform.workshop.domain.WorkOrderPhoto;

/// Photos of a work order, for every signed-in user of the workshop (a mechanic may add to and change only what the
/// service lets them). The bytes need the bearer token like everything else: there is no public URL.
@RestController
@RequestMapping("/api/v1/work-orders/{id}/photos")
class PhotoController {

    private final PhotoService photos;

    PhotoController(PhotoService photos) {
        this.photos = photos;
    }

    @GetMapping
    List<PhotoResponse> list(CurrentUser user, @PathVariable UUID id) {
        return photos.list(user, id).stream().map(PhotoResponse::of).toList();
    }

    /// `multipart/form-data` with the parts `file`, `stage` and, optionally, `caption`. The type is decided by the
    /// first bytes of the file, not by what the client declares.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    PhotoResponse upload(CurrentUser user, @PathVariable UUID id, @RequestParam("file") MultipartFile file,
            @RequestParam("stage") PhotoStage stage, @RequestParam(value = "caption", required = false)
            @Nullable String caption) throws IOException {
        // A file over the multipart limit never gets here (413 from the container); the service checks 5 MB again.
        return PhotoResponse.of(photos.upload(user, id, stage, caption, file.getBytes()));
    }

    /// The bytes, with the sniffed content type and an ETag (the SHA-256 of the file); a matching `If-None-Match` gets
    /// 304 without reading the bytes.
    @GetMapping("/{photoId}/content")
    @Nullable ResponseEntity<byte[]> content(CurrentUser user, @PathVariable UUID id, @PathVariable UUID photoId, WebRequest request) {
        WorkOrderPhoto photo = photos.find(user, id, photoId);
        String etag = "\"" + photo.sha256() + "\"";
        if (request.checkNotModified(etag)) {
            return null;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.type().contentType()))
                .eTag(etag)
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline; filename=\"photo-" + photo.id() + "." + extension(photo) + "\"")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(photos.bytes(photo));
    }

    @PutMapping("/{photoId}")
    PhotoResponse update(CurrentUser user, @PathVariable UUID id, @PathVariable UUID photoId,
            @Valid @RequestBody PhotoUpdateRequest request) {
        return PhotoResponse.of(photos.update(user, id, photoId, request.stage(), request.caption(),
                request.version()));
    }

    @DeleteMapping("/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(CurrentUser user, @PathVariable UUID id, @PathVariable UUID photoId,
            @Valid @RequestBody(required = false) PhotoDeleteRequest request) {
        photos.delete(user, id, photoId, request == null ? null : request.reason());
    }

    private static String extension(WorkOrderPhoto photo) {
        return switch (photo.type()) {
            case JPEG -> "jpg";
            case PNG -> "png";
            case WEBP -> "webp";
        };
    }
}
