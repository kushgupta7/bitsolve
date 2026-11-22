package com.bits.itsm.intelligence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import com.bits.itsm.incident.Incident;
import org.springframework.stereotype.Component;

/**
 * Suggests an urgency for the administrator from what the reporter wrote, with the reasons.
 *
 * Students no longer rate severity themselves, so the description is the only evidence of time pressure.
 * Explainable keyword rules (no model, no API): each matching signal adds a reason. Two or more urgency
 * signals suggest HIGH, one suggests MEDIUM; an explicit "not urgent" with no urgency signal suggests LOW;
 * nothing found means no suggestion at all. The administrator always decides.
 */
@Component
public class UrgencyHinter {
    public record Hint(Incident.Level level, List<String> reasons) { }
    private record Signal(Pattern pattern, String reason) { }

    private static Pattern p(String regex) { return Pattern.compile(regex, Pattern.CASE_INSENSITIVE); }

    /** Phrases that lower urgency; removed from the text first so "not urgent" never counts as "urgent". */
    private static final Pattern NOT_URGENT = p("\\b(not (that )?urgent|no rush|no hurry|whenever (you can|possible)|when(ever)? convenient|"
            + "at your convenience|low priority|not important|minor issue|cosmetic)\\b");

    private static final List<Signal> URGENT = List.of(
            new Signal(p("\\b(exams?|quiz(zes)?|mid-?sems?|compre(hensives?)?|end-?sems?|viva|lab test|online test)\\b"), "mentions an exam or test"),
            new Signal(p("\\b(deadline|due (today|tonight|tomorrow|in \\d+)|submission|last date|submit (it )?(by|before))\\b"), "mentions a deadline"),
            new Signal(p("(\\b(can ?not|can't|cant|unable to|not able to)\\b|\\b(nobody|no one|none of us)\\b.{0,30}\\bcan\\b).{0,30}\\b(log ?in|sign ?in|access|submit|upload|connect|join|attend|register|pay)"), "says people are blocked from essential work"),
            new Signal(p("\\b(whole|entire|all|every|everyone|everybody|nobody|no one|many)\\b.{0,25}\\b(hostels?|floors?|blocks?|wings?|labs?|campus|class(es)?|students|buildings?|departments?|library|rooms?|batch)\\b"), "says many people are affected"),
            new Signal(p("\\b(right now|urgent(ly)?|asap|immediately|emergency|during (the )?(class|lecture|lab|exam)|class is (on|going on)|lecture is (on|going on))\\b"), "says it is needed right now"));

    public Hint suggest(String title, String description) {
        String text = ((title == null ? "" : title) + " . " + (description == null ? "" : description)).toLowerCase(Locale.ROOT);
        boolean calm = NOT_URGENT.matcher(text).find();
        String rest = NOT_URGENT.matcher(text).replaceAll(" ");
        List<String> reasons = new ArrayList<>();
        for (Signal signal : URGENT) if (signal.pattern().matcher(rest).find()) reasons.add(signal.reason());
        if (reasons.size() >= 2) return new Hint(Incident.Level.HIGH, List.copyOf(reasons));
        if (reasons.size() == 1) return new Hint(Incident.Level.MEDIUM, List.copyOf(reasons));
        if (calm) return new Hint(Incident.Level.LOW, List.of("says it is not urgent"));
        return null;
    }
}
