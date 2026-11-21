import React from 'react';
import Notifications from '../Notifications.jsx';
import { technicianNotifications } from '../demoData.js';

export function NotificationCenter({ technicianId, accessibleIds, readIds, onRead, onView }) {
  return <Notifications records={technicianNotifications.filter((item) => item.technicianId === technicianId)} readIds={readIds} onRead={onRead} onOpen={(item) => onView(item.incidentId)} canOpen={(item) => accessibleIds.includes(item.incidentId)} />;
}
