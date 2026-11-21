package com.bits.itsm.user;

import static org.junit.jupiter.api.Assertions.*;
import com.bits.itsm.incident.Incident;
import org.junit.jupiter.api.Test;

/** The demo setup must leave no empty dropdown: every service reportable, every skill held by two technicians. */
class DemoShowcaseSeederTest {
    @Test void everyServiceGetsACriticality() {
        for (var service : Incident.ItService.values()) assertNotNull(DemoShowcaseSeeder.CRITICALITY.get(service), service.name());
    }

    @Test void everySkillIsHeldByAtLeastTwoTechniciansSoTransfersHaveSomeoneToPick() {
        for (var skill : TechnicianSkill.values()) {
            long holders = DemoShowcaseSeeder.SKILLS.values().stream().filter(set -> set.contains(skill)).count();
            assertTrue(holders >= 2, skill + " is held by " + holders + " technician(s)");
        }
    }

    @Test void everyCategoryWithARequiredSkillIsCovered() {
        for (var category : Incident.Category.values()) {
            if (category == Incident.Category.OTHER) continue;
            var skill = category == Incident.Category.APPLICATION ? TechnicianSkill.APPLICATION_SUPPORT : TechnicianSkill.valueOf(category.name());
            assertTrue(DemoShowcaseSeeder.SKILLS.values().stream().anyMatch(set -> set.contains(skill)), category.name());
        }
    }
}
