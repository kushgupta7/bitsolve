package com.bits.itsm.incident.state;

import com.bits.itsm.incident.IncidentStatus;
import static com.bits.itsm.incident.state.LifecycleActor.*;

/** Reported by a student; waiting for an administrator to confirm category and priority. */
final class NewState extends AbstractIncidentState {
    NewState() {
        super(IncidentStatus.NEW);
        allow(IncidentStatus.CLASSIFIED, ADMIN);
        // Duplicate/invalid report rejected by the admin, or withdrawn by the reporter, before any work starts.
        allow(IncidentStatus.CLOSED, ADMIN, STUDENT);
    }
}
