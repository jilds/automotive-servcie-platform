package com.asp.platform.identity.domain;

/// Password rules for accounts that set their own password (activation, bootstrap admin).
public final class Password {

    public static final int MIN_LENGTH = 12;
    /// Bcrypt only uses the first 72 bytes; longer input is refused rather than silently cut.
    public static final int MAX_LENGTH = 72;

    private Password() {
    }

    public static boolean isAcceptable(String password) {
        return password.length() >= MIN_LENGTH && !password.isBlank()
                && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= MAX_LENGTH;
    }

    /// @throws IllegalArgumentException when the password breaks the rules
    public static void requireAcceptable(String password) {
        if (!isAcceptable(password)) {
            throw new IllegalArgumentException("Password must have 12 to 72 characters");
        }
    }
}
