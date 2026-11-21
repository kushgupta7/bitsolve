package com.bits.itsm.user;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Availability status: only AVAILABLE technicians can take new work; BUSY and UNAVAILABLE are skipped. */
class TechnicianAvailabilityTest {
    private final Technician technician = new Technician("Tech", "tech1@hyderabad.bits-pilani.ac.in", "$2-test-only", "NETWORK");

    @Test void newTechnicianStartsAvailable() {
        assertEquals(Technician.Availability.AVAILABLE, technician.getAvailability());
        assertTrue(technician.isAvailable());
    }

    @Test void busyAndUnavailableAreNotEligibleForNewAssignments() {
        technician.changeAvailability(Technician.Availability.BUSY);
        assertFalse(technician.isAvailable());
        technician.changeAvailability(Technician.Availability.UNAVAILABLE);
        assertFalse(technician.isAvailable());
        technician.changeAvailability(Technician.Availability.AVAILABLE);
        assertTrue(technician.isAvailable());
    }

    @Test void missingStatusIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> technician.changeAvailability(null));
        assertEquals(Technician.Availability.AVAILABLE, technician.getAvailability());
    }
}
