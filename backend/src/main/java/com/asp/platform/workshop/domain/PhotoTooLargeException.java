package com.asp.platform.workshop.domain;

/// A photo over 5 MB. 413 `photo-too-large` (mapped by the workshop advice: it is not a conflict).
public class PhotoTooLargeException extends RuntimeException {

    public static final String CODE = "photo-too-large";

    public PhotoTooLargeException() {
        super("A photo can be at most 5 MB");
    }
}
