import React, { useState } from 'react';
import { apiSend } from './api.js';

const BITS_EMAIL = /^[A-Za-z0-9_%+-]+(?:\.[A-Za-z0-9_%+-]+)*@hyderabad\.bits-pilani\.ac\.in$/i;

/** Student self-registration. Technician and administrator accounts are still created by an administrator. */
export default function RegisterForm({ onRegistered, onCancel }) {
  const [values, setValues] = useState({ name: '', email: '', studentNumber: '', password: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function change(event) {
    const { name, value } = event.target;
    setValues(previous => ({ ...previous, [name]: value }));
    setErrors(previous => ({ ...previous, [name]: '' }));
    setMessage('');
  }

  function validate() {
    const next = {};
    if (values.name.trim().length < 2) next.name = 'Enter your full name.';
    if (!BITS_EMAIL.test(values.email.trim())) next.email = 'Use your @hyderabad.bits-pilani.ac.in address.';
    if (!/^[A-Za-z0-9]{4,30}$/.test(values.studentNumber.trim())) next.studentNumber = 'Student ID: 4–30 letters or digits, e.g. 2025A7PS0001H.';
    if (values.password.length < 12) next.password = 'Use at least 12 characters.';
    else if (new TextEncoder().encode(values.password).length > 72) next.password = 'Password must be at most 72 bytes.';
    if (values.confirm !== values.password) next.confirm = 'Passwords do not match.';
    return next;
  }

  async function submit(event) {
    event.preventDefault();
    if (submitting) return;
    const next = validate();
    setErrors(next);
    if (Object.keys(next).length) { event.currentTarget.elements.namedItem(Object.keys(next)[0])?.focus(); return; }
    setSubmitting(true);
    try {
      await apiSend('auth/register', { name: values.name.trim(), email: values.email.trim(), studentNumber: values.studentNumber.trim(), password: values.password });
      onRegistered(values.email.trim());
    } catch (error) {
      setMessage(error.message);
    } finally {
      setSubmitting(false);
    }
  }

  const field = (name, label, type = 'text', autoComplete) => <div className="field">
    <div className="floating-input">
      <input id={`reg-${name}`} name={name} type={type} autoComplete={autoComplete} placeholder=" " value={values[name]} onChange={change} aria-invalid={Boolean(errors[name])} aria-describedby={errors[name] ? `reg-${name}-error` : undefined} />
      <label htmlFor={`reg-${name}`}>{label}</label>
    </div>
    {errors[name] && <p className="field-error" id={`reg-${name}-error`} role="alert">{errors[name]}</p>}
  </div>;

  return <form onSubmit={submit} noValidate aria-busy={submitting} className="register-form">
    {field('name', 'Full name', 'text', 'name')}
    {field('email', 'Institutional email', 'email', 'username')}
    {field('studentNumber', 'Student ID', 'text', 'off')}
    {field('password', 'Password (12+ characters)', 'password', 'new-password')}
    {field('confirm', 'Confirm password', 'password', 'new-password')}
    <button className="submit-button" type="submit" disabled={submitting}>{submitting ? 'Creating account…' : 'Create Student Account'} <span aria-hidden="true">→</span></button>
    <p className="status-message" role="status" aria-hidden={!message}>{message}</p>
    <p className="auth-switch">Already registered? <button type="button" className="link-button" onClick={onCancel}>Sign in</button></p>
  </form>;
}
