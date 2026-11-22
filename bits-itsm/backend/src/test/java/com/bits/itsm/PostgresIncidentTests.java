package com.bits.itsm;

import java.util.Map;
import com.bits.itsm.user.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "BITSOLVE_DB_TESTS", matches = "true")
@SpringBootTest(properties = {"spring.datasource.url=${BITSOLVE_TEST_DB_URL}", "bitsolve.demo.seed-enabled=true", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc @ActiveProfiles("dev")
class PostgresIncidentTests {
    @org.junit.jupiter.api.BeforeEach void configureTestService() {
        // Explicit test fixture; production initialization never supplies criticality.
        testCatalog.update("SERVICE", "CAMPUS_WIFI", "Campus Wi-Fi", true, com.bits.itsm.incident.Incident.Level.MEDIUM);
    }
    @Autowired com.bits.itsm.incident.CatalogService testCatalog;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired com.bits.itsm.support.IncidentLookup incidentLookup;
    final JsonMapper json = JsonMapper.builder().build();
    String email(String name) { return name + "@hyderabad.bits-pilani.ac.in"; }
    JsonNode postAs(String name, String path, Object body, int status) throws Exception {
        var result = mvc.perform(post("/api/incidents" + path).with(user(email(name))).with(csrf())
                        .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().is(status)).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
    JsonNode getAs(String name, String path, int status) throws Exception {
        var result = mvc.perform(get("/api/incidents" + path).with(user(email(name))))
                .andExpect(status().is(status)).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
    Map<String, Object> payload() { return Map.of("title", "Network problem", "description", "Campus network is unavailable in the library.", "category", "NETWORK", "affectedService", "CAMPUS_WIFI", "impact", "HIGH", "urgency", "MEDIUM", "reporterId", 99999, "status", "CLOSED", "assignedTechnician", 99999); }
    long create() throws Exception { return postAs("student01", "", payload(), 201).get("id").asLong(); }
    void assign(long id) throws Exception {
        postAs("admin01", "/" + id + "/classify", Map.of("category", "NETWORK"), 200);
        postAs("admin01", "/" + id + "/assign", Map.of("technicianId", users.findByEmail(email("technician04")).orElseThrow().getId()), 200);
    }
    void investigate(long id) throws Exception { assign(id); postAs("technician04", "/" + id + "/acknowledge", Map.of(), 200); postAs("technician04", "/" + id + "/start", Map.of(), 200); }
    void resolve(long id) throws Exception { postAs("technician04", "/" + id + "/resolve", Map.of("summary", "Replaced cable", "steps", "Tested connectivity"), 200); }

    @Test void creationUsesSessionIdentityAndRestrictsReads() throws Exception {
        long id = create(); JsonNode incident = getAs("student01", "/" + id, 200);
        assertEquals("NEW", incident.get("status").asText()); assertEquals("PENDING", incident.get("priority").asText());
        assertEquals(users.findByEmail(email("student01")).orElseThrow().getId().longValue(), incident.get("reporter").get("id").asLong());
        assertTrue(incident.get("assignedTechnician").isNull());
        getAs("student02", "/" + id, 403); getAs("technician02", "/" + id, 403); getAs("admin01", "/" + id, 200);
        JsonNode mine = getAs("student01", "/my?search=" + id + "&status=NEW&category=NETWORK&priority=PENDING", 200);
        assertTrue(mine.isArray()); assertTrue(mine.size() > 0);
        for (JsonNode row : getAs("student02", "/my", 200)) assertNotEquals(id, row.get("id").asLong());
        assertFalse(incident.toString().contains("password"));
        mvc.perform(get("/api/incidents/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/incidents").with(user(email("student01"))).contentType("application/json").content(json.writeValueAsString(payload()))).andExpect(status().isForbidden());
    }
    @Test void validatesPayloadAndRole() throws Exception {
        postAs("student01", "", Map.of(), 400);
        var invalid = new java.util.HashMap<>(payload()); invalid.put("category", "FORGED"); postAs("student01", "", invalid, 400);
        invalid = new java.util.HashMap<>(payload()); invalid.put("affectedService", "FORGED"); postAs("student01", "", invalid, 400);
        invalid = new java.util.HashMap<>(payload()); invalid.put("description", "                    "); postAs("student01", "", invalid, 400);
        postAs("technician04", "", payload(), 403); postAs("admin01", "", payload(), 403);
        getAs("student01", "/999999999", 404);
    }
    @Test void lifecyclePersistsHistoryAndFeedbackAndRejectsUnauthorizedActions() throws Exception {
        long id = create();
        postAs("student01", "/" + id + "/verify", Map.of("confirmed", true), 409);
        postAs("admin01", "/" + id + "/close", Map.of(), 409);
        postAs("student01", "/" + id + "/classify", Map.of("category", "NETWORK"), 403);
        assign(id);
        postAs("technician02", "/" + id + "/acknowledge", Map.of(), 403);
        postAs("technician04", "/" + id + "/resolve", Map.of("summary", "Fixed", "steps", "Tested"), 409);
        postAs("technician04", "/" + id + "/acknowledge", Map.of(), 200);
        postAs("technician04", "/" + id + "/start", Map.of(), 200);
        postAs("technician04", "/" + id + "/resolve", Map.of("summary", "Fixed", "steps", " "), 400);
        resolve(id);
        postAs("student02", "/" + id + "/verify", Map.of("confirmed", true), 403);
        postAs("student01", "/" + id + "/verify", Map.of("confirmed", true), 200);
        postAs("student01", "/" + id + "/close", Map.of(), 403);
        postAs("admin01", "/" + id + "/close", Map.of(), 200);
        postAs("student01", "/" + id + "/feedback", Map.of("rating", 6), 400);
        postAs("student02", "/" + id + "/feedback", Map.of("rating", 5), 403);
        postAs("student01", "/" + id + "/feedback", Map.of("rating", 5, "comment", "Helpful"), 200);
        postAs("student01", "/" + id + "/feedback", Map.of("rating", 4), 409);
        JsonNode saved = getAs("student01", "/" + id, 200);
        assertEquals("CLOSED", saved.get("status").asText()); assertEquals(5, saved.get("feedback").get("rating").asInt());
        assertEquals(10, saved.get("history").size());
        assertTrue(saved.get("history").toString().contains("PRIORITY_ASSIGNED"));
        assertFalse(saved.get("closedAt").isNull()); assertFalse(saved.get("verifiedAt").isNull());
        postAs("technician04", "/" + id + "/start", Map.of(), 409);
        assertEquals(saved.get("history"), getAs("admin01", "/" + id, 200).get("history"));
    }
    @Test void privateNotesSupportRepliesAndRejectedResolutionPersist() throws Exception {
        long id = create(); investigate(id);
        postAs("technician04", "/" + id + "/notes", Map.of("content", "Private diagnosis", "visibility", "INTERNAL"), 200);
        postAs("technician04", "/" + id + "/notes", Map.of("content", "Checking connection", "visibility", "STUDENT_VISIBLE"), 200);
        JsonNode student = getAs("student01", "/" + id, 200);
        assertEquals(1, student.get("notes").size()); assertFalse(student.toString().contains("Private diagnosis")); assertFalse(student.toString().contains("INTERNAL"));
        assertEquals(2, getAs("admin01", "/" + id, 200).get("notes").size());
        postAs("technician04", "/" + id + "/information", Map.of("content", "Which device?"), 200);
        postAs("student02", "/" + id + "/reply", Map.of("content", "Unrelated reply"), 403);
        postAs("student01", "/" + id + "/reply", Map.of("content", "My laptop"), 200);
        resolve(id);
        postAs("student01", "/" + id + "/verify", Map.of("confirmed", false, "reason", " "), 400);
        postAs("student01", "/" + id + "/verify", Map.of("confirmed", false, "reason", "Connection still drops"), 200);
        JsonNode reopened = getAs("technician04", "/" + id, 200);
        assertEquals("IN_PROGRESS", reopened.get("status").asText()); assertTrue(reopened.get("resolvedAt").isNull());
        assertTrue(reopened.toString().contains("Connection still drops")); assertTrue(reopened.toString().contains("Replaced cable"));
        resolve(id); postAs("student01", "/" + id + "/verify", Map.of("confirmed", true), 200);
    }
    @Test void assignmentRejectsStudentsDisabledAndUnavailableTechnicians() throws Exception {
        long id = create(); postAs("admin01", "/" + id + "/classify", Map.of("category", "HARDWARE"), 200);
        JsonNode historyBefore = getAs("admin01", "/" + id, 200).get("history");
        postAs("admin01", "/" + id + "/assign", Map.of("technicianId", users.findByEmail(email("student02")).orElseThrow().getId()), 400);
        Technician technician = (Technician) users.findByEmail(email("technician04")).orElseThrow();
        try {
            technician.setAvailable(false); users.saveAndFlush(technician);
            postAs("admin01", "/" + id + "/assign", Map.of("technicianId", technician.getId()), 409);
            technician.setAvailable(true); technician.setEnabled(false); users.saveAndFlush(technician);
            postAs("admin01", "/" + id + "/assign", Map.of("technicianId", technician.getId()), 409);
        } finally { technician.setAvailable(true); technician.setEnabled(true); users.saveAndFlush(technician); }
        assertEquals("CLASSIFIED", getAs("admin01", "/" + id, 200).get("status").asText());
        assertEquals(historyBefore, getAs("admin01", "/" + id, 200).get("history"));
    }
    @Test void adminSlaUsesCapturedDeadlinesAndRejectsOtherRoles() throws Exception {
        long id = create();
        JsonNode incident = postAs("admin01", "/" + id + "/classify", Map.of("category", "NETWORK"), 200);
        var response = mvc.perform(get("/api/admin/sla").with(user(email("admin01")).roles("ADMIN")))
                .andExpect(status().isOk()).andReturn();
        JsonNode row = java.util.stream.StreamSupport.stream(json.readTree(response.getResponse().getContentAsString()).spliterator(), false)
                .filter(item -> item.get("incidentId").asLong() == id).findFirst().orElseThrow();
        var created = java.time.Instant.parse(incident.get("createdAt").asText());
        assertEquals(created.plusSeconds(15 * 60), java.time.Instant.parse(row.get("responseDeadline").asText()));
        assertEquals(created.plusSeconds(2 * 60 * 60), java.time.Instant.parse(row.get("resolutionDeadline").asText()));
        assertEquals("ON_TRACK", row.get("responseStatus").asText());
        assertEquals("ON_TRACK", row.get("resolutionStatus").asText());
        var snapshot = incidentLookup.findById(id).orElseThrow();
        assertEquals(created.plusSeconds(15 * 60), snapshot.responseDeadline());
        assertEquals(created.plusSeconds(2 * 60 * 60), snapshot.resolutionDeadline());
        mvc.perform(get("/api/admin/sla").with(user(email("student01")).roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/sla").with(user(email("technician01")).roles("TECHNICIAN"))).andExpect(status().isForbidden());
    }
    @Test void reporterCanEditOnlyTheirOwnUnclassifiedReport() throws Exception {
        long id = create();
        Map<String, String> change = Map.of("title", "Updated network problem", "description", "The library network is unavailable on both my devices.");
        postAs("student02", "/" + id + "/edit", change, 403);
        assertEquals("Updated network problem", postAs("student01", "/" + id + "/edit", change, 200).get("title").asText());
        postAs("admin01", "/" + id + "/classify", Map.of("category", "NETWORK"), 200);
        postAs("student01", "/" + id + "/edit", change, 409);
    }
    @Test void reporterNoLongerRatesSeverityAndAdminAssessesItAtClassification() throws Exception {
        var report = new java.util.HashMap<String, Object>(payload());
        report.remove("impact"); report.remove("urgency");
        JsonNode created = postAs("student01", "", report, 201);
        assertEquals("MEDIUM", created.get("impact").asText());   // provisional until assessed
        assertEquals("MEDIUM", created.get("urgency").asText());
        long id = created.get("id").asLong();
        JsonNode classified = postAs("admin01", "/" + id + "/classify", Map.of("category", "NETWORK", "impact", "HIGH", "urgency", "HIGH"), 200);
        assertEquals("HIGH", classified.get("impact").asText());
        assertEquals("HIGH", classified.get("urgency").asText());
        assertEquals("CLASSIFIED", classified.get("status").asText());
        assertNotEquals("PENDING", classified.get("priority").asText());
    }
    @Test void reporterWithdrawsAdminRejectsDuplicateAndTechnicianResumesWithoutReply() throws Exception {
        long mistaken = create();
        postAs("technician04", "/" + mistaken + "/withdraw", Map.of("content", "not mine"), 403);
        JsonNode withdrawn = postAs("student01", "/" + mistaken + "/withdraw", Map.of("content", "It works again"), 200);
        assertEquals("CLOSED", withdrawn.get("status").asText());
        assertTrue(withdrawn.get("resolvedAt").isNull());
        long duplicate = create();
        JsonNode rejected = postAs("admin01", "/" + duplicate + "/withdraw", Map.of("content", "Duplicate of " + mistaken), 200);
        assertEquals("CLOSED", rejected.get("status").asText());
        postAs("admin01", "/" + duplicate + "/withdraw", Map.of("content", "again"), 409);   // only a NEW report can be withdrawn
        long waiting = create(); investigate(waiting);
        postAs("technician04", "/" + waiting + "/information", Map.of("content", "Which building?"), 200);
        JsonNode resumed = postAs("technician04", "/" + waiting + "/resume", Map.of(), 200);
        assertEquals("IN_PROGRESS", resumed.get("status").asText());
        postAs("student01", "/" + waiting + "/resume", Map.of(), 403);
    }

    @Test void technicianWithOpenIncidentsCannotBeDisabled() throws Exception {
        long id = create(); investigate(id);
        long technicianId = users.findByEmail(email("technician04")).orElseThrow().getId();
        mvc.perform(put("/api/admin/users/" + technicianId).with(user(email("admin01")).roles("ADMIN")).with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"name\":\"Network Technician\",\"enabled\":false}"))
           .andExpect(status().isConflict());
        assertTrue(users.findByEmail(email("technician04")).orElseThrow().isEnabled());
    }
    JsonNode getApi(String name, String path, int status) throws Exception {
        String role = name.startsWith("admin") ? "ADMIN" : name.startsWith("technician") ? "TECHNICIAN" : "STUDENT";
        var result = mvc.perform(get("/api" + path).with(user(email(name)).roles(role))).andExpect(status().is(status)).andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? null : json.readTree(body);
    }

    @Test void meTooFindsTheOpenReportAndCountsEachStudentOnceWithoutChangingPriority() throws Exception {
        long id = create();   // student01, Campus Wi-Fi: "Network problem" / "Campus network is unavailable in the library."
        String query = "/incidents/similar-open?service=CAMPUS_WIFI&text=" + java.net.URLEncoder.encode("network unavailable in library", "UTF-8");
        JsonNode found = getApi("student02", query, 200);
        assertTrue(found.findValues("id").stream().anyMatch(n -> n.asLong() == id), "student02 is shown the open report");
        assertTrue(found.toString().contains("\"title\"") && !found.toString().contains("reporter") && !found.toString().contains("description"),
                "only title, status, time and count are shown");
        assertTrue(getApi("student01", query, 200).findValues("id").stream().noneMatch(n -> n.asLong() == id), "never your own report");
        assertEquals(0, getApi("student02", "/incidents/similar-open?service=EMAIL&text=network+unavailable+library", 200).size(), "personal services never show others' reports");

        JsonNode first = postAs("student02", "/" + id + "/confirm", Map.of(), 200);
        assertEquals(1, first.get("confirmations").asInt());
        assertTrue(first.get("confirmedByYou").asBoolean());
        assertEquals(1, postAs("student02", "/" + id + "/confirm", Map.of(), 200).get("confirmations").asInt(), "a second click does not count again");
        assertEquals(2, postAs("student03", "/" + id + "/confirm", Map.of(), 200).get("confirmations").asInt());
        postAs("student01", "/" + id + "/confirm", Map.of(), 409);       // the reporter
        postAs("technician04", "/" + id + "/confirm", Map.of(), 403);    // students only

        JsonNode incident = getAs("admin01", "/" + id, 200);
        assertEquals(2, incident.get("confirmations").asInt(), "the administrator sees the count");
        postAs("admin01", "/" + id + "/classify", Map.of("category", "NETWORK", "impact", "LOW", "urgency", "LOW"), 200);
        JsonNode classified = getAs("admin01", "/" + id, 200);
        long control = create();
        postAs("admin01", "/" + control + "/classify", Map.of("category", "NETWORK", "impact", "LOW", "urgency", "LOW"), 200);
        assertEquals(getAs("admin01", "/" + control, 200).get("priority").asText(), classified.get("priority").asText(), "confirmations never change priority");
    }

    @Test void confirmersAreToldWhenTheIssueIsFixedAndPersonalOrClosedIssuesCannotBeConfirmed() throws Exception {
        long id = create();
        postAs("student02", "/" + id + "/confirm", Map.of(), 200);
        investigate(id);
        resolve(id);
        JsonNode inbox = getApi("student02", "/notifications", 200);
        boolean told = false;
        for (JsonNode n : inbox) told |= "CONFIRMED_ISSUE_UPDATE".equals(n.get("type").asText()) && n.get("incidentId").asLong() == id;
        assertTrue(told, "the confirming student is notified of the fix");
        postAs("student03", "/" + id + "/confirm", Map.of(), 409);       // no longer open

        testCatalog.update("SERVICE", "EMAIL", "Email Services", true, com.bits.itsm.incident.Incident.Level.MEDIUM);
        var personal = new java.util.HashMap<String, Object>(payload());
        personal.put("affectedService", "EMAIL"); personal.put("category", "APPLICATION");
        long email = postAs("student01", "", personal, 201).get("id").asLong();
        postAs("student02", "/" + email + "/confirm", Map.of(), 409);
    }

    @Test void urgencyHintIsForTheAdminOnlyAndOnlyBeforeClassification() throws Exception {
        var urgent = new java.util.HashMap<String, Object>(payload());
        urgent.put("title", "Portal down before the exam");
        urgent.put("description", "Nobody in the whole hostel can log in to submit the exam assignment due today.");
        long id = postAs("student01", "", urgent, 201).get("id").asLong();
        JsonNode hint = getApi("admin01", "/admin/incidents/" + id + "/urgency-hint", 200);
        assertTrue(hint.get("available").asBoolean());
        assertEquals("HIGH", hint.get("level").asText());
        assertTrue(hint.get("reasons").size() >= 2);
        getApi("student01", "/admin/incidents/" + id + "/urgency-hint", 403);
        postAs("admin01", "/" + id + "/classify", Map.of("category", "NETWORK", "impact", "HIGH", "urgency", "HIGH"), 200);
        assertFalse(getApi("admin01", "/admin/incidents/" + id + "/urgency-hint", 200).get("available").asBoolean());
    }

    @Test void resolutionEstimateUsesHistoryAndRespectsAccess() throws Exception {
        long id = create(); assign(id);
        String priority = getAs("admin01", "/" + id, 200).get("priority").asText();
        // Five resolved incidents of the same category and priority, 1–5 hours each.
        long reporter = users.findByEmail(email("student01")).orElseThrow().getId();
        for (int h = 1; h <= 5; h++) {
            jdbc.update("insert into incidents(title, description, reporter_id, category, affected_service, impact, urgency, priority, status, created_at, updated_at, resolved_at, closed_at, confirmations) "
                    + "values ('History', 'Resolved network incident for the estimate.', ?, 'NETWORK', 'CAMPUS_WIFI', 'LOW', 'LOW', ?, 'CLOSED', now() - make_interval(hours => ?), now(), now(), now(), 0)",
                    reporter, priority, h + 1);
        }
        JsonNode estimate = getApi("student01", "/incidents/" + id + "/resolution-estimate", 200);
        assertTrue(estimate.get("available").asBoolean());
        assertTrue(estimate.get("basedOn").asInt() >= 5);
        assertTrue(estimate.get("usuallyWithinMinutes").asLong() >= estimate.get("typicalMinutes").asLong());
        getApi("student02", "/incidents/" + id + "/resolution-estimate", 403);   // not their incident
        assertFalse(getApi("student01", "/incidents/" + create() + "/resolution-estimate", 200).get("available").asBoolean(), "NEW incidents have no priority yet");
    }
}
