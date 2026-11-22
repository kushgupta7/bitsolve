package com.bits.itsm.user;

import static org.junit.jupiter.api.Assertions.*;
import com.bits.itsm.exception.ValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Mobile numbers are optional, Indian, and stored in one canonical form. */
class PhoneNumberTest {
    @ParameterizedTest
    @ValueSource(strings = {"9876543210", "+91 98765 43210", "+919876543210", "919876543210", "09876543210", "98765-43210", "(98765) 43210"})
    void acceptedFormatsAreStoredCanonically(String input) {
        assertEquals("+91 98765 43210", PhoneNumber.normalize(input));
    }

    @Test void blankMeansNoNumber() {
        assertNull(PhoneNumber.normalize(null));
        assertNull(PhoneNumber.normalize("   "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "5876543210", "+1 555 010 0100", "98765432101", "abcdefghij"})
    void invalidNumbersAreRejected(String input) {
        assertThrows(ValidationException.class, () -> PhoneNumber.normalize(input));
    }
}
