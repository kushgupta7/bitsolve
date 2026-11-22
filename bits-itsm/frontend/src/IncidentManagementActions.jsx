import React, { useEffect, useState } from 'react';
import TechnicianOperations, { operationLabels } from './pages/TechnicianOperations.jsx';
import { apiRequest } from './api.js';
import { Recommendations, UrgencyHint } from './IncidentAssist.jsx';
import ReasonAction from './ReasonAction.jsx';

const LEVELS = [['LOW', 'Low'], ['MEDIUM', 'Medium'], ['HIGH', 'High']];
const IMPACT_HINTS = { LOW: 'one person', MEDIUM: 'several people or a group', HIGH: 'a whole service or many people' };
const URGENCY_HINTS = { LOW: 'can wait', MEDIUM: 'needed soon', HIGH: 'blocking work now' };

export default function IncidentManagementActions({ incident, role, options, onAction }) {
  const [action, setAction] = useState(null);
  const [category, setCategory] = useState(incident.category);
  const [impact, setImpact] = useState('');
  const [urgency, setUrgency] = useState('');
  const [autoAssign, setAutoAssign] = useState(true);
  const [technicianId, setTechnicianId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [related, setRelated] = useState([]);
  useEffect(() => {
    if (role !== 'ADMIN') return;
    let active = true;
    apiRequest(`admin/incidents/${incident.id}/related`).then(data => { if (active) setRelated(data); }).catch(error => { if (active) setError(error.message); });
    return () => { active = false; };
  }, [incident.id, role]);
  const [candidates, setCandidates] = useState([]);
  const [candidatesLoading, setCandidatesLoading] = useState(false);
  const assignable = ['CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'AWAITING_INFO'].includes(incident.status);
  useEffect(() => {
    if (role !== 'ADMIN' || !assignable) return;
    let active = true;
    setCandidatesLoading(true); setTechnicianId('');
    apiRequest(`incidents/${incident.id}/eligible-technicians`).then(data => { if (active) setCandidates(data); })
      .catch(error => { if (active) setError(error.message); }).finally(() => { if (active) setCandidatesLoading(false); });
    return () => { active = false; };
  }, [incident, role, assignable]);
  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    setBusy(true); setError(''); setMessage('');
    try {
      const action = event.nativeEvent.submitter?.value === 'auto' ? 'auto-assign' : incident.status === 'NEW' ? 'classify' : assignable ? 'assign' : 'close';
      await onAction(action, action === 'classify' ? { category, impact, urgency } : action === 'assign' ? { technicianId: Number(technicianId) } : {});
      // One step for the admin: classify and hand the incident to a technician straight away.
      if (action === 'classify' && autoAssign) {
        try { await onAction('auto-assign', {}); setMessage('Classified and assigned.'); }
        catch (assignError) { setMessage(`Classified. Not assigned yet: ${assignError.message}`); }
      } else setMessage(action === 'classify' ? 'Classified.' : 'Saved.');
    } catch (error) { setError(error.message); }
    finally { setBusy(false); }
  }
  async function resumeWork() {
    if (busy) return;
    setBusy(true); setError(''); setMessage('');
    try { await onAction('resume', {}); setMessage('Work resumed; the SLA clock is running again.'); }
    catch (error) { setError(error.message); }
    finally { setBusy(false); }
  }
  // Acknowledging (response SLA) and starting are separate states, but one click for the technician.
  async function startWork() {
    if (busy) return;
    setBusy(true); setError(''); setMessage('');
    try {
      if (incident.status === 'ASSIGNED') await onAction('acknowledge', {});
      await onAction('start', {});
      setMessage('Work started.');
    } catch (error) { setError(error.message); }
    finally { setBusy(false); }
  }
  return <>
    {role === 'ADMIN' && <section className="student-detail-section"><h3>Possible duplicates</h3>{related.length ? <ul>{related.map(item => <li key={item.incidentId}>Incident {item.incidentId}: {item.title} ({Math.round(item.similarity * 100)}% similar)</li>)}</ul> : <p>None found.</p>}</section>}
    {role === 'ADMIN' && assignable && <Recommendations incident={incident} onPick={id => setTechnicianId(candidates.some(c => String(c.id) === id) ? id : '')} />}
    {role === 'ADMIN' && (assignable || ['NEW', 'VERIFIED'].includes(incident.status)) && <form className="report-form" onSubmit={submit}>
      {incident.status === 'NEW' && incident.categorySuggestion && <p>Suggested category: <strong>{incident.categorySuggestion.category}</strong>. {incident.categorySuggestion.explanation}</p>}
      {incident.status === 'NEW' && <div className="report-field"><label htmlFor="classify-category">Confirm Category</label><select id="classify-category" value={category} onChange={event => setCategory(event.target.value)} required>{options.categories.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}</select></div>}
      {incident.status === 'NEW' && <div className="report-row">
        <div className="report-field"><label htmlFor="classify-impact">Impact (how many are affected)</label><select id="classify-impact" value={impact} onChange={event => setImpact(event.target.value)} required><option value="">Assess impact</option>{LEVELS.map(([value, label]) => <option key={value} value={value}>{label}: {IMPACT_HINTS[value]}</option>)}</select></div>
        <div className="report-field"><label htmlFor="classify-urgency">Urgency (how soon it is needed)</label><select id="classify-urgency" value={urgency} onChange={event => setUrgency(event.target.value)} required><option value="">Assess urgency</option>{LEVELS.map(([value, label]) => <option key={value} value={value}>{label}: {URGENCY_HINTS[value]}</option>)}</select></div>
      </div>}
      {incident.status === 'NEW' && <UrgencyHint incident={incident} onUse={level => setUrgency(level)} />}
      {incident.status === 'NEW' && incident.confirmations > 0 && <p className="report-guidance"><strong>{incident.confirmations} other student{incident.confirmations === 1 ? '' : 's'}</strong> confirmed they are affected ("me too"). This is a signal; it does not change the priority by itself.</p>}
      {incident.status === 'NEW' && incident.affectedUsers && <p className="report-guidance">Reporter estimates {incident.affectedUsers} people affected{incident.affectedUsers > 100 ? ' (more than 100 raises the priority one level)' : ''}.</p>}
      {incident.status === 'NEW' && <label className="report-check"><input type="checkbox" checked={autoAssign} onChange={event => setAutoAssign(event.target.checked)} /> Assign a technician automatically</label>}
      {assignable && <div className="report-field"><label htmlFor="assign-technician">Assign Technician</label><select id="assign-technician" value={technicianId} onChange={event => setTechnicianId(event.target.value)}><option value="">{candidatesLoading ? 'Loading eligible technicians…' : 'Choose an eligible technician'}</option>{candidates.map(person => <option key={person.id} value={person.id}>{person.name} · {person.total}/{person.maxWorkload} active</option>)}</select>{!candidatesLoading && !candidates.length && <p>No eligible technicians currently available.</p>}</div>}
      <div className="report-actions"><button className="incident-view" type="submit" disabled={busy || (assignable && !technicianId) || (incident.status === 'NEW' && (!impact || !urgency))}>{busy ? 'Saving…' : incident.status === 'NEW' ? (autoAssign ? 'Classify & Assign' : 'Classify') : assignable ? incident.status === 'CLASSIFIED' ? 'Assign Technician' : 'Reassign Technician' : 'Close Incident'}</button>
      {incident.status === 'CLASSIFIED' && <button className="incident-view" type="submit" value="auto" disabled={busy}>Automatically Assign</button>}</div>
    </form>}
    {role === 'TECHNICIAN' && <div className="report-actions">{incident.primaryOwner && ['ASSIGNED', 'ACKNOWLEDGED'].includes(incident.status) && <button className="incident-view" disabled={busy} onClick={startWork}>{busy ? 'Starting…' : 'Start Work'}</button>}{(incident.primaryOwner ? [...(incident.status === 'IN_PROGRESS' ? ['note', 'information', 'resolution'] : incident.status === 'AWAITING_INFO' ? ['note'] : []), ...(['ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'AWAITING_INFO'].includes(incident.status) ? ['transfer', 'collaboration'] : [])] : ['IN_PROGRESS', 'AWAITING_INFO'].includes(incident.status) ? ['note'] : []).map(action => <button className="incident-view" key={action} onClick={() => setAction(action)}>{operationLabels[action]}</button>)}</div>}
    {role === 'TECHNICIAN' && incident.status === 'AWAITING_INFO' && <p role="status">Waiting for the reporter's reply. The resolution SLA clock is paused.</p>}
    {role === 'TECHNICIAN' && incident.primaryOwner && incident.status === 'AWAITING_INFO' && <div className="report-actions"><button className="incident-view" disabled={busy} onClick={resumeWork}>Resume without reply</button></div>}
    {role === 'ADMIN' && incident.status === 'NEW' && <ReasonAction id={`reject-${incident.id}`} label="Close as duplicate or invalid" prompt="Reason (the reporter sees this)" placeholder="e.g. Duplicate of incident 1047, already being fixed" confirmLabel="Close without action" onConfirm={reason => onAction('withdraw', { content: reason })} />}
    {error && <p className="report-error" role="alert">{error}</p>}{message && <p role="status">{message}</p>}
    {action && <TechnicianOperations key={`${incident.id}-${action}`} incident={incident} action={action} onAction={onAction} onClose={() => setAction(null)} />}
  </>;
}
