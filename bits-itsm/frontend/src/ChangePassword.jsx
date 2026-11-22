import React, { useState } from 'react';
import { apiPost } from './api.js';

/** Signed-in user changes their own password (same rule as registration: 12+ characters, not the e-mail name). */
export default function ChangePassword() {
  const [values, setValues] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const [state, setState] = useState({ busy: false, error: '', done: false });
  const set = (key) => (event) => setValues(previous => ({ ...previous, [key]: event.target.value }));
  async function submit(event) {
    event.preventDefault();
    if (values.newPassword !== values.confirm) { setState({ busy: false, error: 'The new passwords do not match.', done: false }); return; }
    setState({ busy: true, error: '', done: false });
    try {
      await apiPost('auth/password', { currentPassword: values.currentPassword, newPassword: values.newPassword });
      setValues({ currentPassword: '', newPassword: '', confirm: '' });
      setState({ busy: false, error: '', done: true });
    } catch (error) { setState({ busy: false, error: error.message, done: false }); }
  }
  return <form className="report-form" onSubmit={submit} aria-labelledby="change-password-title">
    <h2 id="change-password-title">Change password</h2>
    <div className="report-field"><label htmlFor="current-password">Current password</label><input id="current-password" type="password" autoComplete="current-password" required value={values.currentPassword} onChange={set('currentPassword')} /></div>
    <div className="report-row">
      <div className="report-field"><label htmlFor="new-password">New password (12+ characters)</label><input id="new-password" type="password" autoComplete="new-password" required minLength="12" maxLength="72" value={values.newPassword} onChange={set('newPassword')} /></div>
      <div className="report-field"><label htmlFor="confirm-password">Repeat new password</label><input id="confirm-password" type="password" autoComplete="new-password" required minLength="12" maxLength="72" value={values.confirm} onChange={set('confirm')} /></div>
    </div>
    {state.error && <p className="report-error" role="alert">{state.error}</p>}
    {state.done && <p role="status">Password changed.</p>}
    <div className="report-actions"><button className="incident-view" type="submit" disabled={state.busy}>{state.busy ? 'Saving…' : 'Change password'}</button></div>
  </form>;
}
