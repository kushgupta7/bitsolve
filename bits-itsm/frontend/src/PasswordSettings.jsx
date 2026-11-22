import React, { useEffect, useState } from 'react';
import { apiRequest, apiPost } from './api.js';

export default function PasswordSettings({ reset = false }) {
  const [enabled, setEnabled] = useState(false);
  const [open, setOpen] = useState(false);
  const [current, setCurrent] = useState('');
  const [token, setToken] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  useEffect(() => { let active=true; apiRequest('auth/password-capabilities').then(data => {if(active) setEnabled(data.enabled);}).catch(() => {}); return () => {active=false;}; }, []);
  if (!enabled) return null;
  async function submit(event) {
    event.preventDefault(); if (busy) return;
    setError(''); setMessage('');
    if (password !== confirmation) { setError('Passwords do not match.'); return; }
    if (password.length < 12 || !/[A-Za-z]/.test(password) || !/[0-9]/.test(password) || new TextEncoder().encode(password).length > 72) { setError('Use at least 12 characters, a letter and a number, within 72 bytes.'); return; }
    setBusy(true);
    try {
      const result = await apiPost(`auth/${reset ? 'reset-password' : 'change-password'}`, {token, currentPassword:current, password, confirmation});
      setCurrent(''); setToken(''); setPassword(''); setConfirmation(''); setMessage(result.message); setOpen(false);
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  return <section className="profile-security-box">
    {!open && !message && <button className="incident-view" type="button" onClick={() => setOpen(true)}>{reset ? 'Use a password reset code' : 'Change password'}</button>}
    {message && <p role="status">{message}</p>}
    {open && <form className="report-form" onSubmit={submit}>
      <h3>{reset ? 'Reset password' : 'Change password'}</h3>
      <p>{reset ? 'Ask your administrator for a single-use code. Codes expire after 30 minutes.' : 'Enter your current password. Updating it signs out all existing sessions.'}</p>
      <label>{reset ? 'Reset code' : 'Current password'}<input type="password" autoComplete={reset ? 'one-time-code' : 'current-password'} required value={reset ? token : current} onChange={e => reset ? setToken(e.target.value) : setCurrent(e.target.value)} disabled={busy} /></label>
      <label>New password<input type="password" autoComplete="new-password" minLength={12} maxLength={72} required value={password} onChange={e => setPassword(e.target.value)} disabled={busy} /></label>
      <label>Confirm new password<input type="password" autoComplete="new-password" minLength={12} maxLength={72} required value={confirmation} onChange={e => setConfirmation(e.target.value)} disabled={busy} /></label>
      <p>12 or more characters, including a letter and a number. Maximum 72 UTF-8 bytes.</p>
      {error && <p className="report-error" role="alert">{error}</p>}
      <div className="report-actions"><button className="incident-view" disabled={busy}>{busy ? 'Saving…' : 'Update password'}</button><button className="incident-view" type="button" disabled={busy} onClick={() => {setOpen(false);setCurrent('');setToken('');setPassword('');setConfirmation('');setError('');}}>Cancel</button></div>
    </form>}
  </section>;
}

export function AdminPasswordReset({ account }) {
  const [supported,setSupported]=useState(false);
  const [busy,setBusy]=useState(false);
  const [result,setResult]=useState(null);
  const [error,setError]=useState('');
  useEffect(() => { let active=true; apiRequest('auth/me').then(data => {if(active) setSupported(data.passwordManagement);}).catch(() => {}); return () => {active=false;}; }, []);
  if(!supported || !account.enabled) return null;
  async function initiate() {
    setBusy(true); setError(''); setResult(null);
    try { setResult(await apiPost(`admin/users/${account.id}/password-reset`)); } catch(e) {setError(e.message);} finally {setBusy(false);}
  }
  return <section className="profile-security-box"><h3>Password recovery for {account.name}</h3><p>Generate a single-use code and share it privately with the account owner. Existing passwords are never displayed.</p>
    <button className="incident-view" type="button" disabled={busy} onClick={initiate}>{busy ? 'Generating…' : 'Generate reset code'}</button>
    {error && <p role="alert">{error}</p>}
    {result && <div role="status"><label>Single-use reset code<input readOnly autoComplete="off" value={result.token} onFocus={e => e.target.select()} /></label><p>Expires {new Date(result.expiresAt).toLocaleString()}. The owner can enter this code using “Use a password reset code” on the login page.</p><button type="button" className="incident-view" onClick={() => setResult(null)}>Hide code</button></div>}
  </section>;
}
