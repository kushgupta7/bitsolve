package com.bits.itsm.incident;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.bits.itsm.intelligence.ResolutionTimeEstimator;
import com.bits.itsm.intelligence.SimilarOpenIssues;
import com.bits.itsm.intelligence.UrgencyHinter;
import com.bits.itsm.user.Role;
import com.bits.itsm.user.User;
import com.bits.itsm.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Three intelligent assists around an incident:
 *  1. "Is this the same problem?" + "Me too" for students (duplicate prevention), with anti-abuse rules:
 *     shared campus services only, open reports from the last 48 h only, never your own, one confirmation per
 *     student (database primary key), only title/status/time/count are shown (never the reporter or the
 *     description), and confirmations never change priority (the administrator sees them as a signal).
 *  2. Urgency hint for the administrator (from the description, with reasons).
 *  3. "Usually fixed within" estimate from resolved history.
 */
@Service
public class IncidentAssistService {
    public record SimilarIssue(Long id, String title, IncidentStatus status, Instant reportedAt, int confirmations, boolean confirmedByYou) { }
    public record UrgencyHintView(boolean available, Incident.Level level, List<String> reasons) { }
    public record EstimateView(boolean available, Long typicalMinutes, Long usuallyWithinMinutes, Integer basedOn, String basis) { }

    private final IncidentRepository incidents;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final UrgencyHinter urgency;
    private final ResolutionTimeEstimator estimator;

    public IncidentAssistService(IncidentRepository incidents, UserRepository users, JdbcTemplate jdbc, UrgencyHinter urgency, ResolutionTimeEstimator estimator) {
        this.incidents = incidents; this.users = users; this.jdbc = jdbc; this.urgency = urgency; this.estimator = estimator;
    }

    private User actor(String email, Role... roles) {
        User user = users.findByEmail(email).filter(User::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in with an enabled account."));
        if (roles.length > 0 && List.of(roles).stream().noneMatch(r -> r == user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This action is not permitted for your role.");
        }
        return user;
    }

    private boolean confirmedBy(Long incidentId, Long studentId) {
        Integer n = jdbc.queryForObject("select count(*) from incident_confirmations where incident_id = ? and student_id = ?", Integer.class, incidentId, studentId);
        return n != null && n > 0;
    }

    private SimilarIssue view(Incident i, Long studentId) {
        return new SimilarIssue(i.getId(), i.getTitle(), i.getStatus(), i.getCreatedAt(), i.getConfirmations(), confirmedBy(i.getId(), studentId));
    }

    /** Open reports on the same shared service that look like what the student is typing (best first, at most 3). */
    @Transactional(readOnly = true)
    public List<SimilarIssue> similar(String email, Incident.ItService service, String text) {
        User student = actor(email, Role.STUDENT);
        if (service == null || !service.isShared() || text == null || text.isBlank()) return List.of();
        Instant now = Instant.now();
        List<Incident> open = incidents.findByAffectedServiceAndCreatedAtAfterOrderByCreatedAtDesc(service, now.minus(SimilarOpenIssues.WINDOW)).stream()
                .filter(Incident::isOpen)
                .filter(i -> !i.getReporter().getId().equals(student.getId()))
                .toList();
        Map<Long, Incident> byId = open.stream().collect(Collectors.toMap(Incident::getId, Function.identity()));
        var candidates = open.stream().map(i -> new SimilarOpenIssues.Candidate(i.getId(), i.getTitle(), i.getDescription(), i.getCreatedAt())).toList();
        return SimilarOpenIssues.find(text, candidates, now).stream().map(m -> view(byId.get(m.id()), student.getId())).toList();
    }

    /** "Me too": idempotent; the same student confirming twice still counts once. */
    @Transactional
    public SimilarIssue confirm(Long id, String email) {
        User student = actor(email, Role.STUDENT);
        Incident incident = incidents.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found."));
        if (incident.getReporter().getId().equals(student.getId())) throw new IllegalStateException("You reported this incident yourself.");
        if (!incident.getAffectedService().isShared()) throw new IllegalStateException("Only issues with shared campus services can be confirmed.");
        if (!incident.isOpen()) throw new IllegalStateException("This issue is no longer open. If it still affects you, please report it.");
        int added = jdbc.update("insert into incident_confirmations(incident_id, student_id, confirmed_at) values (?, ?, ?) on conflict do nothing",
                id, student.getId(), java.sql.Timestamp.from(Instant.now()));
        if (added == 1) incident.addConfirmation();
        incidents.flush();
        return view(incident, student.getId());
    }

    /** Administrator only, and only while the incident still needs classifying. */
    @Transactional(readOnly = true)
    public UrgencyHintView urgencyHint(Long id, String email) {
        actor(email, Role.ADMIN);
        Incident incident = incidents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found."));
        UrgencyHinter.Hint hint = incident.getStatus() == IncidentStatus.NEW ? urgency.suggest(incident.getTitle(), incident.getDescription()) : null;
        return hint == null ? new UrgencyHintView(false, null, List.of()) : new UrgencyHintView(true, hint.level(), hint.reasons());
    }

    /** Anyone who may see the incident; only for classified incidents that are still being worked on. */
    @Transactional(readOnly = true)
    public EstimateView estimate(Long id, IncidentData.View visible) {
        boolean inProgress = List.of(IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS, IncidentStatus.AWAITING_INFO)
                .contains(visible.status());
        Optional<ResolutionTimeEstimator.Estimate> estimate = inProgress
                ? estimator.estimate(visible.category(), visible.priority(), label(visible.category().name())) : Optional.empty();
        return estimate.map(e -> new EstimateView(true, e.typicalMinutes(), e.usuallyWithinMinutes(), e.basedOn(), e.basis()))
                .orElse(new EstimateView(false, null, null, null, null));
    }

    private static String label(String code) {
        String spaced = code.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
