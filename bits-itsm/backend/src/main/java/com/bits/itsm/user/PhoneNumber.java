package com.bits.itsm.user;

import com.bits.itsm.exception.ValidationException;

/** Indian mobile numbers, stored in one canonical form: "+91 98765 43210". */
public final class PhoneNumber {
    private PhoneNumber() { }

    /** Blank means "no number" (null). Accepts +91 / 91 / 0 prefixes, spaces, dashes and brackets. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String digits = raw.replaceAll("[\\s\\-()]", "");
        if (digits.startsWith("+91")) digits = digits.substring(3);
        else if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        else if (digits.length() == 11 && digits.startsWith("0")) digits = digits.substring(1);
        if (!digits.matches("[6-9]\\d{9}")) {
            throw new ValidationException("Enter a 10-digit Indian mobile number, e.g. +91 98765 43210.");
        }
        return "+91 " + digits.substring(0, 5) + " " + digits.substring(5);
    }
}
