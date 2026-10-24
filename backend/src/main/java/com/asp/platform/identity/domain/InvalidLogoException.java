package com.asp.platform.identity.domain;

/// An uploaded logo was refused. The code says why: `logo-too-large`, `logo-unsupported` (not a PNG, SVG or WEBP
/// image) or `logo-unsafe` (an SVG with scripts, event handlers, external references or other active content).
public class InvalidLogoException extends RuntimeException {

    public static final String TOO_LARGE = "logo-too-large";
    public static final String UNSUPPORTED = "logo-unsupported";
    public static final String UNSAFE = "logo-unsafe";

    private final String code;

    public InvalidLogoException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
