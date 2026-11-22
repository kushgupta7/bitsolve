package com.bits.itsm.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import com.bits.itsm.exception.ValidationException;

/** One password rule for every way a password is set: registration, admin-created accounts, change and reset. */
public final class PasswordPolicy {
    private PasswordPolicy() { }

    public static void check(String password, String email) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ValidationException("Password must be at least 12 characters (at most 72 bytes).");
        }
        String local = email == null ? "" : email.trim().split("@")[0].toLowerCase(Locale.ROOT);
        if (!local.isEmpty() && password.toLowerCase(Locale.ROOT).contains(local)) {
            throw new ValidationException("Password must not contain your e-mail name.");
        }
    }
}
