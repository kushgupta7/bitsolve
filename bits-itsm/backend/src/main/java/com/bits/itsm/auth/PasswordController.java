package com.bits.itsm.auth;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.bits.itsm.audit.AuditRecord;
import com.bits.itsm.audit.AuditStore;
import com.bits.itsm.exception.ValidationException;
import com.bits.itsm.user.User;
import com.bits.itsm.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Any signed-in user changes their own password (current password required) or mobile number. */
@RestController
@RequestMapping("/api/auth")
public class PasswordController {
    public record PasswordChange(@NotBlank @Size(max = 200) String currentPassword, @NotBlank @Size(max = 200) String newPassword) { }
    /** Blank clears the number. */
    public record PhoneChange(@Size(max = 30) String phone) { }

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuditStore audit;

    public PasswordController(UserRepository users, PasswordEncoder passwords, AuditStore audit) {
        this.users = users; this.passwords = passwords; this.audit = audit;
    }

    @PostMapping("/password")
    @Transactional
    public Map<String, String> change(@Valid @RequestBody PasswordChange request, Authentication auth) {
        User user = users.findByEmail(auth.getName()).filter(User::isEnabled).orElseThrow(() -> new ValidationException("Account not found."));
        if (!passwords.matches(request.currentPassword(), user.getPasswordHash())) throw new ValidationException("Your current password is not correct.");
        if (request.currentPassword().equals(request.newPassword())) throw new ValidationException("Choose a password different from the current one.");
        PasswordPolicy.check(request.newPassword(), user.getEmail());
        user.changePasswordHash(passwords.encode(request.newPassword()));
        audit.save(new AuditRecord("AUD-" + UUID.randomUUID(), Instant.now(), user.getId(), null, "PASSWORD_CHANGED", null, null, "User changed their password."));
        return Map.of("message", "Password changed.");
    }

    /** Any signed-in user sets or clears their own mobile number for urgent SMS alerts. */
    @PutMapping("/phone")
    @Transactional
    public AuthController.Account phone(@Valid @RequestBody PhoneChange request, Authentication auth) {
        User user = users.findByEmail(auth.getName()).filter(User::isEnabled).orElseThrow(() -> new ValidationException("Account not found."));
        user.changePhone(request.phone());
        audit.save(new AuditRecord("AUD-" + UUID.randomUUID(), Instant.now(), user.getId(), null, "PHONE_UPDATED", null, null,
                user.getPhone() == null ? "User removed their mobile number." : "User updated their mobile number."));
        return AuthController.Account.from(user);
    }
}
