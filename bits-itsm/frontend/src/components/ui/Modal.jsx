import React, { useEffect } from 'react';

export default function Modal({ isOpen, onClose, title, children, footer, maxWidth = '560px' }) {
  useEffect(() => {
    if (!isOpen) return;
    function handleKeyDown(e) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="bits-modal-overlay" onClick={onClose} role="dialog" aria-modal="true" aria-labelledby="modal-title">
      <div className="bits-modal-content" style={{ maxWidth }} onClick={(e) => e.stopPropagation()}>
        <div className="bits-modal-header">
          <h2 id="modal-title" className="bits-modal-title">{title}</h2>
          <button className="bits-modal-close" onClick={onClose} aria-label="Close dialog" type="button">
            &times;
          </button>
        </div>
        <div className="bits-modal-body">{children}</div>
        {footer && <div className="bits-modal-footer">{footer}</div>}
      </div>
    </div>
  );
}
