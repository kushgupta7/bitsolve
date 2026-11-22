package com.bits.itsm.notification.sms;

import java.util.Optional;
import com.bits.itsm.user.User;
import com.bits.itsm.user.UserRepository;
import org.springframework.stereotype.Component;

/** Phone numbers come from the user's own account (Profile, or set by an administrator). */
@Component
public class UserPhoneDirectory implements PhoneDirectory {
    private final UserRepository users;
    public UserPhoneDirectory(UserRepository users) { this.users = users; }
    @Override public Optional<String> phoneOf(Long userId) {
        return userId == null ? Optional.empty() : users.findById(userId).filter(User::isEnabled).map(User::getPhone);
    }
}
