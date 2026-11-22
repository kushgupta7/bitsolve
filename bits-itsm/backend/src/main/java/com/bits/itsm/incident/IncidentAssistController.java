package com.bits.itsm.incident;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Endpoints for the incident assists (see {@link IncidentAssistService}). */
@RestController
@RequestMapping("/api")
public class IncidentAssistController {
    private final IncidentAssistService assist;
    private final IncidentService incidents;

    public IncidentAssistController(IncidentAssistService assist, IncidentService incidents) { this.assist = assist; this.incidents = incidents; }

    @GetMapping("/incidents/similar-open")
    public List<IncidentAssistService.SimilarIssue> similar(@RequestParam(required = false) Incident.ItService service,
                                                            @RequestParam(defaultValue = "") String text, Authentication auth) {
        return assist.similar(auth.getName(), service, text.length() > 2000 ? text.substring(0, 2000) : text);
    }

    @PostMapping("/incidents/{id}/confirm")
    public IncidentAssistService.SimilarIssue confirm(@PathVariable Long id, Authentication auth) { return assist.confirm(id, auth.getName()); }

    @GetMapping("/admin/incidents/{id}/urgency-hint")
    public IncidentAssistService.UrgencyHintView urgencyHint(@PathVariable Long id, Authentication auth) { return assist.urgencyHint(id, auth.getName()); }

    @GetMapping("/incidents/{id}/resolution-estimate")
    public IncidentAssistService.EstimateView estimate(@PathVariable Long id, Authentication auth) {
        return assist.estimate(id, incidents.get(id, auth.getName())); // get() enforces who may see the incident
    }
}
