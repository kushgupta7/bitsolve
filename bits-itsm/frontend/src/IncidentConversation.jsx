import React, { useEffect, useState } from 'react';
import { apiRequest, apiPost } from './api.js';

export default function IncidentConversation({ incidentId }) {
  const [conversation, setConversation] = useState(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true, pending = false;
    async function load() {
      if (pending || document.hidden) return;
      pending = true;
      try { const data = await apiRequest(`incidents/${incidentId}/messages`); if (active) { setConversation(data); setError(''); } }
      catch (e) { if (active) setError(e.message); }
      finally { pending = false; }
    }
    load(); const timer = setInterval(load, 5000);
    return () => { active = false; clearInterval(timer); };
  }, [incidentId]);
  async function send(event) {
    event.preventDefault(); if (busy || !message.trim()) return;
    setBusy(true); setError('');
    try { const result = await apiPost(`incidents/${incidentId}/messages`, {content:message.trim()}); setConversation(result); setMessage(''); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function markRead() {
    if (busy || !conversation?.messages.length) return;
    setBusy(true);
    try { await apiPost(`incidents/${incidentId}/messages/read`, {messageId:conversation.messages.at(-1).id}); setConversation(await apiRequest(`incidents/${incidentId}/messages`)); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  return <section className="student-detail-section incident-conversation" aria-label="Incident conversation">
    <div className="dashboard-section-heading"><h3>Conversation {conversation?.unreadCount > 0 && <small>({conversation.unreadCount} unread)</small>}</h3>
      {conversation?.unreadCount > 0 && <button className="incident-view" disabled={busy} onClick={markRead}>Mark messages as read</button>}</div>
    <p className="report-guidance">Shared with the student and incident team. Internal investigation notes remain separate.</p>
    {!conversation && !error && <p role="status">Loading conversation…</p>}
    {conversation && <><ol className="conversation-messages">{conversation.messages.map(m => <li key={m.id}>
      <strong>{m.sender}{m.unread ? ' · Unread' : ''}</strong><time dateTime={m.createdAt}>{new Date(m.createdAt).toLocaleString()}</time><p>{m.content}</p>
    </li>)}</ol>{!conversation.messages.length && <p>No messages yet.</p>}
      {conversation.canSend ? <form onSubmit={send}><label htmlFor={`message-${incidentId}`}>Message the incident team</label>
        <textarea id={`message-${incidentId}`} rows={3} required maxLength={2000} value={message} onChange={e => setMessage(e.target.value)} disabled={busy} />
        <button className="incident-view" disabled={busy || !message.trim()}>{busy ? 'Saving…' : 'Send message'}</button>
      </form> : <p>Messaging is available after technician assignment and before closure.</p>}</>}
    {error && <p className="report-error" role="alert">{error}</p>}
  </section>;
}
