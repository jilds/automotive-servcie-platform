package com.asp.platform.workshop.domain;

import com.asp.platform.shared.NotFoundException;

/// No such photo in the workshop, or not a photo of the given work order. 404 `photo-not-found`.
public class PhotoNotFoundException extends NotFoundException {

    public PhotoNotFoundException() {
        super("photo-not-found", "Photo not found");
    }
}
