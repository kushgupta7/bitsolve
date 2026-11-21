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
