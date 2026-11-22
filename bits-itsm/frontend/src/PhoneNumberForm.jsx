import React, { useState } from 'react';
import { apiSend } from './api.js';

/** Staff set the mobile number used for urgent SMS alerts (SLA breaches, escalations, major incidents). */
export default function PhoneNumberForm({ initial }) {
  const [phone, setPhone] = useState(initial || '');
  const [saved, setSaved] = useState(initial || '');
  const [state, setState] = useState({ busy: false, error: '', message: '' });
  async function submit(event) {
    event.preventDefault();
    setState({ busy: true, error: '', message: '' });
    try {
      const account = await apiSend('auth/phone', { phone }, 'PUT');
      setPhone(account.phone || ''); setSaved(account.phone || '');
      setState({ busy: false, error: '', message: account.phone ? 'Saved. Urgent alerts will be texted to this number.' : 'Number removed. You will get in-app and e-mail alerts only.' });
    } catch (error) { setState({ busy: false, error: error.message, message: '' }); }
  }
  return <form className="report-form" onSubmit={submit} aria-labelledby="phone-title">
    <h2 id="phone-title">Mobile number for urgent alerts</h2>
    <p className="report-guidance">Used only for SLA breaches, escalations and major incidents. Leave empty to get no SMS.</p>
    <div className="report-field"><label htmlFor="profile-phone">Mobile number</label>
      <input id="profile-phone" type="tel" inputMode="tel" autoComplete="tel" maxLength="20" placeholder="+91 98765 43210" value={phone} onChange={event => setPhone(event.target.value)} /></div>
    {state.error && <p className="report-error" role="alert">{state.error}</p>}
    {state.message && <p role="status">{state.message}</p>}
    <div className="report-actions"><button className="incident-view" type="submit" disabled={state.busy || phone.trim() === saved}>{state.busy ? 'Saving…' : 'Save number'}</button></div>
  </form>;
}
