import React, { useEffect, useState } from 'react';
import { apiRequest } from './api.js';

/** Intelligent feature D: top-3 technician recommendations with the reasons behind each score. */
export function Recommendations({ incident, onPick }) {
  const [items, setItems] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    apiRequest(`incidents/${incident.id}/recommendations`).then(data => { if (active) setItems(data); }).catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [incident.id, incident.status]);
  return <section className="student-detail-section"><h3>Smart technician recommendation</h3>
    <p>Score = 35% skill match + 25% free capacity + 20% experience + 20% past fixes on this service.</p>
    {error ? <p className="report-error">{error}</p> : !items ? <p role="status">Ranking technicians…</p> : items.length ? <ol className="assist-list">{items.map(r => <li key={r.technicianId}>
      <strong>{r.name}</strong> · score {r.score.toFixed(2)} · {r.workload}/{r.maxWorkload} active
      <div className="assist-reasons">{r.reasons.join(' · ')}</div>
      {onPick && <button type="button" className="report-clear" onClick={() => onPick(String(r.technicianId))}>Select</button>}
    </li>)}</ol> : <p>No eligible technician is available.</p>}
  </section>;
}

/** "45 min", "1 h 30 min", "2 days 3 h": durations for people, not machines. */
export function humanDuration(minutes) {
  if (minutes < 60) return `${minutes} min`;
  if (minutes < 24 * 60) { const h = Math.floor(minutes / 60), m = minutes % 60; return m ? `${h} h ${m} min` : `${h} h`; }
  const d = Math.floor(minutes / 1440), h = Math.round((minutes % 1440) / 60);
  return h ? `${d} day${d === 1 ? '' : 's'} ${h} h` : `${d} day${d === 1 ? '' : 's'}`;
}

const ESTIMATE_STATUSES = ['CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'AWAITING_INFO'];

/** "Usually fixed within …" from how long similar resolved incidents took. Shows nothing without enough history. */
export function ResolutionEstimate({ incident }) {
  const [estimate, setEstimate] = useState(null);
  const relevant = ESTIMATE_STATUSES.includes(incident.status);
  useEffect(() => {
    if (!relevant) { setEstimate(null); return undefined; }
    let active = true;
    apiRequest(`incidents/${incident.id}/resolution-estimate`).then(data => { if (active) setEstimate(data); }).catch(() => { if (active) setEstimate(null); });
    return () => { active = false; };
  }, [incident.id, incident.status, relevant]);
  if (!relevant || !estimate?.available) return null;
  return <section className="student-detail-section estimate-card" aria-label="Estimated fix time">
    <h3>Usually fixed within about {humanDuration(estimate.usuallyWithinMinutes)}</h3>
    <p>3 out of 4 similar incidents were fixed within that time; the typical one took {humanDuration(estimate.typicalMinutes)}.
      Based on {estimate.basedOn} resolved incidents ({estimate.basis}). This is history, not a promise; your SLA deadline still applies.</p>
  </section>;
}

/** Administrator: urgency suggested from what the reporter wrote, with the reasons. */
export function UrgencyHint({ incident, onUse }) {
  const [hint, setHint] = useState(null);
  useEffect(() => {
    if (incident.status !== 'NEW') { setHint(null); return undefined; }
    let active = true;
    apiRequest(`admin/incidents/${incident.id}/urgency-hint`).then(data => { if (active) setHint(data); }).catch(() => { if (active) setHint(null); });
    return () => { active = false; };
  }, [incident.id, incident.status]);
  if (!hint?.available) return null;
  const label = hint.level.charAt(0) + hint.level.slice(1).toLowerCase();
  return <div className="assist-hint" role="note">
    <p><strong>Suggested urgency: {label}</strong>, because the report {hint.reasons.join('; ')}.</p>
    {onUse && <button type="button" className="report-clear" onClick={() => onUse(hint.level)}>Use {label}</button>}
  </div>;
}
