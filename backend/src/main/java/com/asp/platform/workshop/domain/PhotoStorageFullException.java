package com.asp.platform.workshop.domain;

import com.asp.platform.shared.ConflictException;

/// The workshop's photos already use all the storage it may have. 409 `photo-storage-full`.
public class PhotoStorageFullException extends ConflictException {

    public PhotoStorageFullException() {
        super("photo-storage-full", "The workshop's photo storage is full; delete photos you no longer need");
    }
}
