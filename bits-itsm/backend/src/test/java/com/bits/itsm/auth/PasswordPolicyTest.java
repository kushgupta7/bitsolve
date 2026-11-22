package com.bits.itsm.auth;

import static org.junit.jupiter.api.Assertions.*;
import com.bits.itsm.exception.ValidationException;
import org.junit.jupiter.api.Test;

/** One password rule for registration, admin-created accounts, password change and admin reset. */
class PasswordPolicyTest {
    @Test void acceptsTwelveOrMoreCharactersWithoutTheEmailName() {
        assertDoesNotThrow(() -> PasswordPolicy.check("Campus-Wifi-2026", "student01@hyderabad.bits-pilani.ac.in"));
    }
    @Test void rejectsShortTooLongOrEmailBasedPasswords() {
        assertThrows(ValidationException.class, () -> PasswordPolicy.check("short-pw", "a@hyderabad.bits-pilani.ac.in"));
        assertThrows(ValidationException.class, () -> PasswordPolicy.check("x".repeat(73), "a@hyderabad.bits-pilani.ac.in"));
        assertThrows(ValidationException.class, () -> PasswordPolicy.check("Student01-is-me!", "student01@hyderabad.bits-pilani.ac.in"));
        assertThrows(ValidationException.class, () -> PasswordPolicy.check(null, "a@hyderabad.bits-pilani.ac.in"));
    }
}
