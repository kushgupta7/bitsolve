import React, { useEffect, useState } from 'react';
import { apiPost, apiRequest } from './api.js';
import { readableLabel } from './MyIncidents.jsx';

function ago(iso) {
  const minutes = Math.max(1, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  return `${hours} h ago`;
}

/**
 * "Is this the same problem?" while the student writes a report: open reports on the same shared campus
 * service that look similar, with a one-click "This affects me too" instead of a duplicate report.
 * Only titles and status are shown; the server enforces every rule (shared services, 48 h, one per student).
 */
export default function SimilarIssues({ service, text }) {
  const [matches, setMatches] = useState([]);
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    if (!service || text.trim().split(/\s+/).filter(Boolean).length < 2) { setMatches([]); return undefined; }
    let active = true;
    const timer = setTimeout(() => {
      apiRequest(`incidents/similar-open?service=${encodeURIComponent(service)}&text=${encodeURIComponent(text.slice(0, 500))}`)
        .then(data => { if (active) { setMatches(data); setError(''); } })
        .catch(() => { if (active) setMatches([]); });
    }, 500);
    return () => { active = false; clearTimeout(timer); };
  }, [service, text]);

  async function confirm(id) {
    setBusy(id); setError('');
    try {
      const updated = await apiPost(`incidents/${id}/confirm`, {});
      setMatches(previous => previous.map(item => item.id === id ? updated : item));
    } catch (failure) { setError(failure.message); }
    finally { setBusy(null); }
  }

  if (!matches.length) return null;
  return <section className="similar-issues" aria-label="Similar open reports">
    <h3>Already reported? These open reports look similar</h3>
    <ul>{matches.map(item => <li key={item.id}>
      <div><strong>{item.title}</strong>
        <span>{readableLabel(item.status)} · reported {ago(item.reportedAt)}{item.confirmations ? ` · ${item.confirmations} other student${item.confirmations === 1 ? '' : 's'} affected` : ''}</span></div>
      {item.confirmedByYou
        ? <p className="similar-done" role="status">You're on it. We'll notify you when it's fixed, so no need to report it again.</p>
        : <button type="button" className="incident-view" disabled={busy === item.id} onClick={() => confirm(item.id)}>{busy === item.id ? 'Saving…' : 'This affects me too'}</button>}
    </li>)}</ul>
    {error && <p className="report-error" role="alert">{error}</p>}
    <p className="report-guidance">A different problem? Carry on and submit your report.</p>
  </section>;
}
