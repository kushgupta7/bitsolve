package com.bits.itsm.intelligence;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.bits.itsm.incident.Incident;
import org.junit.jupiter.api.Test;

/** The three assists: similar open issues ("me too"), urgency hint, resolution-time estimate. */
class IncidentAssistsTest {
    private final Instant now = Instant.parse("2026-11-22T10:00:00Z");
    private SimilarOpenIssues.Candidate c(long id, String title, String description, Duration age) {
        return new SimilarOpenIssues.Candidate(id, title, description, now.minus(age));
    }

    // ---------- "Is this the same problem?" ----------
    @Test void halfTypedTitleFindsTheOpenReportOnTheSameProblem() {
        var library = c(1, "Wi-Fi down in library", "Wireless keeps disconnecting on the second floor of the library.", Duration.ofMinutes(20));
        var hostel = c(2, "Wi-Fi slow in hostel", "Speed is very slow in hostel block C at night.", Duration.ofMinutes(30));
        var matches = SimilarOpenIssues.find("wifi not working in library", List.of(library, hostel), now);
        assertEquals(List.of(1L), matches.stream().map(SimilarOpenIssues.Match::id).toList());
    }

    @Test void theServiceNameAloneIsNotEnoughAndShortTextShowsNothing() {
        var library = c(1, "Wi-Fi down in library", "Wireless keeps disconnecting in the library.", Duration.ofMinutes(20));
        assertTrue(SimilarOpenIssues.find("wifi", List.of(library), now).isEmpty());
        assertTrue(SimilarOpenIssues.find("wifi problem in my room", List.of(library), now).isEmpty());
    }

    @Test void onlyRecentReportsAreSuggestedAndAtMostThree() {
        var old = c(9, "Wi-Fi down in library", "Wireless down in the library.", SimilarOpenIssues.WINDOW.plusHours(1));
        assertTrue(SimilarOpenIssues.find("wifi down library", List.of(old), now).isEmpty());
        var many = List.of(1L, 2L, 3L, 4L).stream().map(id -> c(id, "Wi-Fi down in library", "Library wireless down.", Duration.ofMinutes(id))).toList();
        assertEquals(SimilarOpenIssues.MAX_RESULTS, SimilarOpenIssues.find("wifi down library", many, now).size());
    }

    // ---------- Urgency hint ----------
    private final UrgencyHinter hinter = new UrgencyHinter();

    @Test void twoUrgencySignalsSuggestHighWithReasons() {
        var hint = hinter.suggest("Portal down before exam", "Nobody in the whole hostel can log in to submit the assignment for the exam today.");
        assertEquals(Incident.Level.HIGH, hint.level());
        assertTrue(hint.reasons().contains("mentions an exam or test"));
        assertTrue(hint.reasons().contains("says people are blocked from essential work"));
    }

    @Test void oneSignalSuggestsMediumAndNotUrgentSuggestsLow() {
        assertEquals(Incident.Level.MEDIUM, hinter.suggest("Printer jam", "The deadline for the lab record printout is tomorrow.").level());
        var low = hinter.suggest("Projector colour", "Colours look a bit off. Not urgent, whenever you can.");
        assertEquals(Incident.Level.LOW, low.level());
        assertEquals(List.of("says it is not urgent"), low.reasons());
    }

    @Test void notUrgentIsNeverReadAsUrgentAndPlainTextGivesNoHint() {
        var hint = hinter.suggest("Mouse", "This is not urgent at all, the mouse double-clicks sometimes.");
        assertEquals(Incident.Level.LOW, hint.level());
        assertNull(hinter.suggest("Keyboard key sticky", "The E key on lab PC 12 sticks a little."));
    }

    // ---------- Usually fixed within ----------
    @Test void estimateNeedsEnoughHistoryAndUsesMedianAndSeventyFifthPercentile() {
        assertTrue(ResolutionTimeEstimator.summarize(List.of(600L, 1200L, 1800L, 2400L), "x").isEmpty());   // 4 < 5 samples
        var estimate = ResolutionTimeEstimator.summarize(List.of(3600L, 1800L, 7200L, 600L, 5400L, 2400L, 9000L, 4200L), "Network · P2").orElseThrow();
        assertEquals(60, estimate.typicalMinutes());           // median of 8 (nearest rank) = 3600 s
        assertEquals(90, estimate.usuallyWithinMinutes());     // 75th percentile = 5400 s
        assertEquals(8, estimate.basedOn());
        assertEquals("Network · P2", estimate.basis());
    }

    @Test void longTimesAreRoundedSoTheyNeverLookFalselyPrecise() {
        assertEquals(1, ResolutionTimeEstimator.roundMinutes(20));
        assertEquals(47, ResolutionTimeEstimator.roundMinutes(47 * 60));
        assertEquals(195, ResolutionTimeEstimator.roundMinutes(193 * 60));   // 3 h 13 min -> 3 h 15 min
    }
}
