package com.bits.itsm.auth;

import com.bits.itsm.audit.AuditRecord;
import com.bits.itsm.audit.AuditStore;
import com.bits.itsm.exception.UnauthorizedOperationException;
import com.bits.itsm.exception.ValidationException;
import com.bits.itsm.user.Role;
import com.bits.itsm.user.User;
import com.bits.itsm.user.UserFactory;
import com.bits.itsm.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Student self-registration (brief: "Register/login"). Only STUDENT accounts can be created this way;
 * technicians and administrators are still created by an administrator. Institutional e-mail is
 * mandatory, passwords are BCrypt-hashed, and the feature can be switched off with
 * {@code bitsolve.registration.enabled=false}. The user signs in afterwards through the normal login,
 * so session and CSRF rotation stay in one place.
 */
@RestController
@RequestMapping("/api/auth")
public class RegistrationController {
    static final Pattern STUDENT_ID = Pattern.compile("^[A-Za-z0-9]{4,30}$");

    public record Registration(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 254) String email,
                               @NotBlank @Size(max = 30) String studentNumber, @NotBlank @Size(max = 200) String password) { }

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuditStore audit;
    private final boolean enabled;

    public RegistrationController(UserRepository users, PasswordEncoder passwords, AuditStore audit,
                                  @Value("${bitsolve.registration.enabled:true}") boolean enabled) {
        this.users = users; this.passwords = passwords; this.audit = audit; this.enabled = enabled;
    }

    @GetMapping("/registration") public Map<String, Boolean> available() { return Map.of("enabled", enabled); }

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String, Object> register(@Valid @RequestBody Registration request) {
        if (!enabled) throw new UnauthorizedOperationException("Self-registration is disabled. Ask the IT administrator for an account.");
        validate(request);
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (users.existsByEmail(email)) throw new ValidationException("An account already exists for this e-mail. Sign in instead.");
        User student = UserFactory.create(Role.STUDENT, request.name(), email, passwords.encode(request.password()), request.studentNumber(), null);
        users.saveAndFlush(student);
        audit.save(new AuditRecord("AUD-" + UUID.randomUUID(), Instant.now(), student.getId(), null, "USER_REGISTERED", null, "STUDENT", "Student self-registered: account " + student.getId()));
        return Map.of("id", student.getId(), "email", student.getEmail(), "role", student.getRole().name());
    }

    /** Business validation beyond Bean Validation; each failure is a ValidationException (HTTP 400). */
    static void validate(Registration r) {
        if (r.name().trim().length() < 2) throw new ValidationException("Enter your full name.");
        if (!EmailValidator.isValid(r.email().trim())) throw new ValidationException("Use your @hyderabad.bits-pilani.ac.in e-mail address.");
        if (!STUDENT_ID.matcher(r.studentNumber().trim()).matches()) throw new ValidationException("Student ID must be 4–30 letters or digits, e.g. 2025A7PS0001H.");
        int bytes = r.password().getBytes(StandardCharsets.UTF_8).length;
        if (r.password().length() < 12 || bytes > 72) throw new ValidationException("Password must be at least 12 characters (at most 72 bytes).");
        if (r.password().toLowerCase(java.util.Locale.ROOT).contains(r.email().trim().split("@")[0].toLowerCase(java.util.Locale.ROOT))) {
            throw new ValidationException("Password must not contain your e-mail name.");
        }
    }
}
