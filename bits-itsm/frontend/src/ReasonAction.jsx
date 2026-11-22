import React, { useState } from 'react';

/**
 * A secondary, destructive-ish action that needs a short reason (withdraw a report, reject a duplicate).
 * Collapsed to one quiet button until used, so it never competes with the main action.
 */
export default function ReasonAction({ id, label, prompt, confirmLabel, placeholder, onConfirm }) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  if (!open) return <button type="button" className="report-clear reason-toggle" onClick={() => setOpen(true)}>{label}</button>;
  async function submit(event) {
    event.preventDefault();
    if (!reason.trim()) { setError('Please give a short reason.'); return; }
    setBusy(true); setError('');
    try { await onConfirm(reason.trim()); setOpen(false); setReason(''); }
    catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }
  return <form className="report-form reason-form" onSubmit={submit}>
    <div className="report-field"><label htmlFor={id}>{prompt}</label>
      <textarea id={id} rows="2" maxLength="2000" value={reason} placeholder={placeholder} onChange={event => setReason(event.target.value)} /></div>
    {error && <p className="report-error" role="alert">{error}</p>}
    <div className="report-actions"><button type="submit" className="incident-view danger" disabled={busy}>{busy ? 'Saving…' : confirmLabel}</button>
      <button type="button" className="report-clear" disabled={busy} onClick={() => { setOpen(false); setError(''); }}>Cancel</button></div>
  </form>;
}
