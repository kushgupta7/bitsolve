package com.bits.itsm.intelligence;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import com.bits.itsm.incident.Incident;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * "Usually fixed within …": how long similar incidents actually took, from resolved history.
 *
 * Similar = same category and priority, resolved within the last 180 days. With fewer than
 * {@link #MIN_SAMPLES} of those, it falls back to the same priority across categories; with fewer than that,
 * it gives no estimate rather than a guess. It reports the median ("typically") and the 75th percentile
 * ("3 out of 4 were fixed within"), rounded for display; it is history, not a promise (the SLA is the promise).
 */
@Component
public class ResolutionTimeEstimator {
    public static final int MIN_SAMPLES = 5;
    public static final Duration HISTORY = Duration.ofDays(180);

    public record Estimate(long typicalMinutes, long usuallyWithinMinutes, int basedOn, String basis) { }

    private final JdbcTemplate jdbc;

    public ResolutionTimeEstimator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Estimate> estimate(Incident.Category category, Incident.Priority priority, String categoryLabel) {
        if (category == null || priority == null) return Optional.empty();
        Timestamp since = Timestamp.from(Instant.now().minus(HISTORY));
        List<Long> exact = jdbc.queryForList("select extract(epoch from (resolved_at - created_at))::bigint from incidents "
                + "where resolved_at is not null and resolved_at >= created_at and category = ? and priority = ? and created_at >= ?",
                Long.class, category.name(), priority.name(), since);
        if (exact.size() >= MIN_SAMPLES) return summarize(exact, categoryLabel + " · " + priority.name());
        List<Long> broader = jdbc.queryForList("select extract(epoch from (resolved_at - created_at))::bigint from incidents "
                + "where resolved_at is not null and resolved_at >= created_at and priority = ? and created_at >= ?",
                Long.class, priority.name(), since);
        if (broader.size() >= MIN_SAMPLES) return summarize(broader, "all " + priority.name() + " incidents");
        return Optional.empty();
    }

    /** Pure: median and 75th percentile (nearest rank) of resolution times in seconds. */
    public static Optional<Estimate> summarize(List<Long> seconds, String basis) {
        if (seconds == null || seconds.size() < MIN_SAMPLES) return Optional.empty();
        List<Long> sorted = new ArrayList<>(seconds);
        Collections.sort(sorted);
        long median = sorted.get((int) Math.ceil(0.50 * sorted.size()) - 1);
        long p75 = sorted.get((int) Math.ceil(0.75 * sorted.size()) - 1);
        return Optional.of(new Estimate(roundMinutes(median), roundMinutes(p75), sorted.size(), basis));
    }

    /** Whole minutes, at least 1; above an hour rounded to 15 minutes so it never looks falsely precise. */
    static long roundMinutes(long seconds) {
        long minutes = Math.max(1, Math.round(seconds / 60.0));
        return minutes <= 60 ? minutes : Math.round(minutes / 15.0) * 15;
    }
}
