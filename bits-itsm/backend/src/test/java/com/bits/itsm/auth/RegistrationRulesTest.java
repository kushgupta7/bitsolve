package com.bits.itsm.auth;

import com.bits.itsm.exception.ValidationException;
import com.bits.itsm.user.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Student self-registration rules and the UserFactory that creates the right User subtype. */
class RegistrationRulesTest {
    private static RegistrationController.Registration reg(String name, String email, String id, String password) {
        return new RegistrationController.Registration(name, email, id, password);
    }

    @Test void acceptsAValidStudentRegistration() {
        RegistrationController.validate(reg("Ananya Rao", "f20250001@hyderabad.bits-pilani.ac.in", "2025A7PS0001H", "correct-horse-battery"));
    }

    @Test void rejectsNonInstitutionalEmailShortPasswordBadIdAndPasswordContainingEmailName() {
        assertThrows(ValidationException.class, () -> RegistrationController.validate(reg("Ananya Rao", "kush@gmail.com", "2025A7PS0001H", "correct-horse-battery")));
        assertThrows(ValidationException.class, () -> RegistrationController.validate(reg("Ananya Rao", "f20250001@hyderabad.bits-pilani.ac.in", "2025A7PS0001H", "short-pass")));
        assertThrows(ValidationException.class, () -> RegistrationController.validate(reg("Ananya Rao", "f20250001@hyderabad.bits-pilani.ac.in", "25/A7", "correct-horse-battery")));
        assertThrows(ValidationException.class, () -> RegistrationController.validate(reg("Ananya Rao", "f20250001@hyderabad.bits-pilani.ac.in", "2025A7PS0001H", "my-f20250001-password")));
        assertThrows(ValidationException.class, () -> RegistrationController.validate(reg("K", "f20250001@hyderabad.bits-pilani.ac.in", "2025A7PS0001H", "correct-horse-battery")));
    }

    @Test void passwordBoundaryIsTwelveCharactersAndSeventyTwoBytes() {
        RegistrationController.validate(reg("Ananya Rao", "f20250001@hyderabad.bits-pilani.ac.in", "2025A7PS0001H", "abcdefghijkl"));
        assertThrows(ValidationException.class, () -> RegistrationController.validate(reg("Ananya Rao", "f20250001@hyderabad.bits-pilani.ac.in", "2025A7PS0001H", "abcdefghijk")));
        assertThrows(ValidationException.class, () -> RegistrationController.validate(reg("Ananya Rao", "f20250001@hyderabad.bits-pilani.ac.in", "2025A7PS0001H", "a".repeat(73))));
    }

    @Test void factoryCreatesTheSubtypeForEachRole() {
        String hash = "$2-test-only";
        assertInstanceOf(Student.class, UserFactory.create(Role.STUDENT, " Ananya ", "s@hyderabad.bits-pilani.ac.in", hash, "2025A7PS0001H", null));
        assertInstanceOf(Technician.class, UserFactory.create(Role.TECHNICIAN, "Tech", "t@hyderabad.bits-pilani.ac.in", hash, null, TechnicianSkill.NETWORK));
        assertInstanceOf(Administrator.class, UserFactory.create(Role.ADMIN, "Admin", "a@hyderabad.bits-pilani.ac.in", hash, null, null));
        assertEquals("Ananya", UserFactory.create(Role.STUDENT, " Ananya ", "s@hyderabad.bits-pilani.ac.in", hash, "ID01", null).getName());
        assertThrows(IllegalArgumentException.class, () -> UserFactory.create(Role.STUDENT, "Ananya", "s@hyderabad.bits-pilani.ac.in", hash, " ", null));
        assertThrows(NullPointerException.class, () -> UserFactory.create(Role.TECHNICIAN, "Tech", "t@hyderabad.bits-pilani.ac.in", hash, null, null));
    }
}
