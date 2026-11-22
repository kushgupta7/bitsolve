package com.bits.itsm.notification.sms;

import java.util.Optional;

/** Where the SMS channel finds a recipient's mobile number (the user's own profile; empty when none is on file). */
@FunctionalInterface
public interface PhoneDirectory {
    Optional<String> phoneOf(Long userId);
}
