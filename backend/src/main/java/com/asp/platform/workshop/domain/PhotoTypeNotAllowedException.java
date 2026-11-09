package com.asp.platform.workshop.domain;

/// The bytes are not a JPEG, PNG or WebP image. 415 `photo-type-not-allowed` (mapped by the workshop advice).
public class PhotoTypeNotAllowedException extends RuntimeException {

    public static final String CODE = "photo-type-not-allowed";

    public PhotoTypeNotAllowedException() {
        super("Only JPEG, PNG and WebP photos are accepted");
    }
}
