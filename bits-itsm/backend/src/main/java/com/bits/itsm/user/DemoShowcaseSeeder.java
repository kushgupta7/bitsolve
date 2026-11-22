package com.bits.itsm.user;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import com.bits.itsm.incident.CatalogService;
import com.bits.itsm.incident.Incident;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ready-to-demo configuration for the one-command Docker setup (never in production).
 *
 * Production keeps the rule that a new IT service stays disabled until an administrator chooses its
 * business criticality. For a fresh demo that would leave the report form without services, so this sets a
 * sensible criticality for every still-unconfigured service and gives each demo technician a few skills, so
 * every category has at least two qualified technicians (transfers and collaboration have someone to pick).
 * Only untouched data is changed: a service an admin configured, or a technician whose skills an admin
 * edited, is left alone.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Profile("dev & !prod & !production")
@ConditionalOnProperty(name = "bitsolve.demo.showcase", havingValue = "true")
public class DemoShowcaseSeeder implements ApplicationRunner {
    static final Map<Incident.ItService, Incident.Level> CRITICALITY = Map.of(
            Incident.ItService.CAMPUS_WIFI, Incident.Level.HIGH, Incident.ItService.CAMPUS_NETWORK, Incident.Level.HIGH,
            Incident.ItService.AUTHENTICATION, Incident.Level.HIGH, Incident.ItService.STUDENT_PORTAL, Incident.Level.HIGH,
            Incident.ItService.EMAIL, Incident.Level.MEDIUM, Incident.ItService.LAB_COMPUTERS, Incident.Level.MEDIUM,
            Incident.ItService.LIBRARY, Incident.Level.LOW, Incident.ItService.OTHER, Incident.Level.LOW);
    /** Every skill is held by two technicians; the first skill listed is the one each was seeded with. */
    static final Map<String, Set<TechnicianSkill>> SKILLS = Map.of(
            "technician01", EnumSet.of(TechnicianSkill.HARDWARE, TechnicianSkill.STORAGE, TechnicianSkill.NETWORK, TechnicianSkill.CLOUD),
            "technician02", EnumSet.of(TechnicianSkill.SOFTWARE, TechnicianSkill.APPLICATION_SUPPORT, TechnicianSkill.HARDWARE, TechnicianSkill.AUTHENTICATION),
            "technician03", EnumSet.of(TechnicianSkill.DATABASE, TechnicianSkill.CLOUD, TechnicianSkill.STORAGE, TechnicianSkill.SOFTWARE, TechnicianSkill.SECURITY),
            "technician04", EnumSet.of(TechnicianSkill.NETWORK, TechnicianSkill.AUTHENTICATION, TechnicianSkill.SECURITY, TechnicianSkill.APPLICATION_SUPPORT, TechnicianSkill.DATABASE));

    static final Map<String, String> DEMO_PHONES = Map.of(
            "admin01", "+91 98765 10000", "technician01", "+91 98765 10001", "technician02", "+91 98765 10002",
            "technician03", "+91 98765 10003", "technician04", "+91 98765 10004");

    private final CatalogService catalog;
    private final DemoUserSeeder demoUsers;
    private final UserRepository users;

    public DemoShowcaseSeeder(CatalogService catalog, DemoUserSeeder demoUsers, UserRepository users) {
        this.catalog = catalog;
        this.demoUsers = demoUsers;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        catalog.run(arguments);   // idempotent: makes sure the catalog rows exist first
        demoUsers.run(arguments); // idempotent: makes sure the demo accounts exist first
        int services = 0, technicians = 0;
        for (var entry : catalog.all()) {
            if (!"SERVICE".equals(entry.getKind()) || entry.getCriticality() != null) continue;
            catalog.update("SERVICE", entry.getCode(), entry.getLabel(), true, CRITICALITY.getOrDefault(Incident.ItService.valueOf(entry.getCode()), Incident.Level.MEDIUM));
            services++;
        }
        for (var demo : SKILLS.entrySet()) {
            var user = users.findByEmail(demo.getKey() + "@hyderabad.bits-pilani.ac.in").orElse(null);
            if (!(user instanceof Technician technician) || technician.getSkills().size() != 1) continue; // admin-edited: keep
            technician.configureAssignment(demo.getValue(), technician.getAvailability(), technician.getMaxWorkload());
            technicians++;
        }
        // Example mobile numbers for staff (who receive urgent SMS alerts); students get none. Only empty numbers are filled.
        int phones = 0;
        for (var demo : DEMO_PHONES.entrySet()) {
            var user = users.findByEmail(demo.getKey() + "@hyderabad.bits-pilani.ac.in").orElse(null);
            if (user == null || user.getPhone() != null) continue;
            user.changePhone(demo.getValue());
            phones++;
        }
        if (services + technicians + phones > 0) {
            LoggerFactory.getLogger(getClass()).info("Demo showcase: configured {} services, {} technician skill sets and {} staff phone numbers.", services, technicians, phones);
        }
    }
}
