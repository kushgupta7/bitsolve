package com.bits.itsm.intelligence;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Is this the same problem?" while a student is still typing a report.
 *
 * Matches what the student has typed against recent open reports on the same shared service. The score is
 * containment: the share of the student's words that also appear in the other report (synonyms normalised,
 * "not working" treated like "down"), because a short half-typed title would never reach a symmetric
 * Jaccard threshold against a full report. At least two shared words are required, so the service name
 * alone ("wifi") never produces a match.
 */
public final class SimilarOpenIssues {
    public static final Duration WINDOW = Duration.ofHours(48);
    public static final double MIN_CONTAINMENT = 0.5;
    public static final int MIN_SHARED_WORDS = 2;
    public static final int MAX_RESULTS = 3;

    /** The minimum a candidate needs; the caller has already checked service, open status and reporter. */
    public record Candidate(Long id, String title, String description, Instant createdAt) { }
    public record Match(Long id, double score) { }

    private SimilarOpenIssues() { }

    public static Set<String> words(String text) {
        return TextSimilarity.tokens(text).stream().map(w -> w.equals("work") ? "fail" : w).collect(Collectors.toSet());
    }

    /** Best matches first; empty when the student has not typed enough to compare. */
    public static List<Match> find(String typed, List<Candidate> candidates, Instant now) {
        Set<String> mine = words(typed);
        if (mine.size() < MIN_SHARED_WORDS) return List.of();
        return candidates.stream()
                .filter(c -> c.createdAt() != null && !c.createdAt().isBefore(now.minus(WINDOW)))
                .map(c -> {
                    Set<String> shared = new HashSet<>(mine);
                    shared.retainAll(words(c.title() + " " + c.description()));
                    double score = shared.size() < MIN_SHARED_WORDS ? 0 : (double) shared.size() / mine.size();
                    return new Match(c.id(), score);
                })
                .filter(m -> m.score() >= MIN_CONTAINMENT)
                .sorted(Comparator.comparingDouble(Match::score).reversed())
                .limit(MAX_RESULTS)
                .toList();
    }
}
