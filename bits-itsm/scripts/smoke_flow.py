"""End-to-end check of the main workflow against a running BITSolve (used by CI after `docker compose up`).

student reports (no impact/urgency) -> admin classifies with impact/urgency and auto-assigns
-> technician starts work (acknowledge + start) -> info request + resume -> transfer has someone to pick -> resolve -> student verifies; a mistaken report is withdrawn.
Usage: python3 smoke_flow.py [base_url]
"""
import http.cookiejar
import json
import sys
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
PASSWORD = "BitsolveDemo2026"
DOMAIN = "@hyderabad.bits-pilani.ac.in"


class Session:
    def __init__(self, email):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.email = email
        self.call("POST", "/api/auth/login", {"email": email, "password": PASSWORD})

    def call(self, method, path, body=None, expect=(200, 201)):
        headers = {"Content-Type": "application/json"}
        if method != "GET":
            csrf = json.loads(self.opener.open(BASE + "/api/auth/csrf").read())
            headers[csrf["headerName"]] = csrf["token"]
        request = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
        try:
            response = self.opener.open(request)
            status, text = response.status, response.read().decode()
        except urllib.error.HTTPError as error:
            status, text = error.code, error.read().decode()
        if status not in expect:
            raise SystemExit(f"FAIL {self.email} {method} {path} -> HTTP {status}: {text[:300]}")
        return json.loads(text) if text else None


def step(message):
    print("  ok:", message)


student = Session("student01" + DOMAIN)
created = student.call("POST", "/api/incidents", {
    "title": "Smoke test: Wi-Fi drops in library",
    "description": "Campus Wi-Fi keeps disconnecting on the second floor of the library every few minutes.",
    "category": "NETWORK", "affectedService": "CAMPUS_WIFI", "affectedUsers": 40})
incident_id = created["id"]
assert created["status"] == "NEW", created["status"]
step(f"student reported incident {incident_id} without rating impact/urgency (provisional {created['impact']}/{created['urgency']})")

admin = Session("admin01" + DOMAIN)
classified = admin.call("POST", f"/api/incidents/{incident_id}/classify", {"category": "NETWORK", "impact": "HIGH", "urgency": "HIGH"})
assert classified["status"] == "CLASSIFIED" and classified["impact"] == "HIGH", classified
assigned = admin.call("POST", f"/api/incidents/{incident_id}/auto-assign", {})
assert assigned["status"] == "ASSIGNED" and assigned["assignedTechnician"], assigned
technician_id = assigned["assignedTechnician"]["id"]
step(f"admin classified (priority {assigned['priority']}) and auto-assigned to {assigned['assignedTechnician']['name']}")

email = next(user["email"] for user in admin.call("GET", "/api/admin/users") if user["id"] == technician_id)
technician = Session(email)
technician.call("POST", f"/api/incidents/{incident_id}/acknowledge", {})
started = technician.call("POST", f"/api/incidents/{incident_id}/start", {})
assert started["status"] == "IN_PROGRESS", started["status"]
step("technician started work (acknowledge + start)")

asked = technician.call("POST", f"/api/incidents/{incident_id}/information", {"content": "Which SSID are you on?"})
assert asked["status"] == "AWAITING_INFO", asked["status"]
resumed = technician.call("POST", f"/api/incidents/{incident_id}/resume", {})
assert resumed["status"] == "IN_PROGRESS", resumed["status"]
step("technician asked for information, then resumed without a reply")

candidates = technician.call("GET", f"/api/incidents/{incident_id}/eligible-technicians")
assert candidates, "transfer dropdown would be empty"
step(f"transfer can go to {len(candidates)} other qualified technician(s)")

helpers = technician.call("GET", f"/api/incidents/{incident_id}/eligible-technicians?collaboration=true&skill=SECURITY")
assert helpers, "collaborator dropdown would be empty"
collab = technician.call("POST", f"/api/incidents/{incident_id}/collaboration-requests", {
    "technicianId": helpers[0]["id"], "requiredSkill": "SECURITY", "reason": "Check for a rogue access point", "responsibility": "Scan the floor for rogue APs"})
admin.call("POST", f"/api/admin/collaboration-requests/{collab['id']}/approve", {"notes": "Approved"})
assert admin.call("GET", f"/api/incidents/{incident_id}")["collaborators"], "collaborator was not added"
step(f"collaboration request approved: {helpers[0]['name']} joined")

team = {member["technician"]["id"] for member in admin.call("GET", f"/api/incidents/{incident_id}")["collaborators"]}
candidates = [c for c in technician.call("GET", f"/api/incidents/{incident_id}/eligible-technicians") if c["id"] not in team] or candidates
transfer = technician.call("POST", f"/api/incidents/{incident_id}/transfer-requests", {
    "technicianId": candidates[0]["id"], "reason": "HIGH_WORKLOAD", "handoverNotes": "AP-L2-03 suspected; logs attached."})
admin.call("POST", f"/api/admin/transfer-requests/{transfer['id']}/approve", {"notes": "Approved"})
moved = admin.call("GET", f"/api/incidents/{incident_id}")
assert moved["assignedTechnician"]["id"] == candidates[0]["id"], "transfer did not change the primary technician"
technician = Session(next(user["email"] for user in admin.call("GET", "/api/admin/users") if user["id"] == candidates[0]["id"]))
step(f"transfer approved: now owned by {moved['assignedTechnician']['name']}")

workload = technician.call("PUT", "/api/technician/availability", {"availability": "BUSY"})
assert workload["availability"] == "BUSY"
technician.call("PUT", "/api/technician/availability", {"availability": "AVAILABLE"})
step("technician toggled availability")

resolved = technician.call("POST", f"/api/incidents/{incident_id}/resolve", {"summary": "Access point restarted.", "steps": "Rebooted AP-L2-03 and checked roaming."})
assert resolved["status"] == "RESOLVED", resolved["status"]
verified = student.call("POST", f"/api/incidents/{incident_id}/verify", {"confirmed": True})
assert verified["status"] == "VERIFIED", verified["status"]
step("technician resolved and student verified")
mistake = student.call("POST", "/api/incidents", {
    "title": "Smoke test: projector not working",
    "description": "The projector in room F-102 shows no signal from the HDMI cable at all.",
    "category": "HARDWARE", "affectedService": "LAB_COMPUTERS"})
withdrawn = student.call("POST", f"/api/incidents/{mistake['id']}/withdraw", {"content": "Reported by mistake"})
assert withdrawn["status"] == "CLOSED" and withdrawn["resolvedAt"] is None, withdrawn["status"]
step("student withdrew a report made by mistake")
# E-mail really goes out (Docker routes it to MailHog on port 8025); the outbox is polled every few seconds.
import time
for _ in range(30):
    try:
        total = json.loads(urllib.request.urlopen(BASE.rsplit(":", 1)[0] + ":8025/api/v2/messages?limit=1").read())["total"]
    except Exception:
        total = 0
    if total:
        break
    time.sleep(2)
assert total, "no e-mail reached MailHog"
step(f"{total} e-mail(s) delivered to MailHog")
print("Workflow smoke test passed.")
