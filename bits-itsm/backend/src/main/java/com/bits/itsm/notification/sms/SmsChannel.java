package com.bits.itsm.notification.sms;

import com.bits.itsm.event.EventType;
import com.bits.itsm.exception.ExternalServiceException;
import com.bits.itsm.notification.Notification;
import com.bits.itsm.notification.NotificationChannel;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SMS channel. Only urgent events (configurable through {@code itsm.sms.events}) go by SMS, so users are not
 * spammed, and only to people who have a mobile number on their account; anyone without one is logged as
 * NO_NUMBER instead of being sent to an invented number. Delivery is best-effort: a provider failure is
 * logged and never rolls back the business transaction. The provider itself is simulated (see the Adapter).
 */
@Component
public class SmsChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(SmsChannel.class);
    static final int HISTORY = 50;
    public enum Status { SENT, FAILED, NO_NUMBER }
    /** One SMS attempt as shown in the admin outbox. */
    public record SentSms(Instant at, Long recipientId, String recipientName, String phone, String text, String receipt, Status status) {
        public boolean delivered() { return status == Status.SENT; }
    }
    private final SmsGateway gateway;
    private final Set<EventType> events;
    private final PhoneDirectory phones;
    private final SmsLog history;

    @Autowired
    public SmsChannel(SmsGateway gateway, @Value("${itsm.sms.events:SLA_BREACHED,INCIDENT_ESCALATED,MAJOR_INCIDENT_DETECTED}") String events,
                      PhoneDirectory phones, SmsLog history) {
        this.gateway = gateway;
        this.phones = phones;
        this.history = history;
        EnumSet<EventType> parsed = EnumSet.noneOf(EventType.class);
        Arrays.stream(events.split(",")).map(String::trim).filter(x -> !x.isEmpty()).map(EventType::valueOf).forEach(parsed::add);
        this.events = parsed;
    }
    /** Unit tests: a fixed phone directory and an in-memory log. */
    public SmsChannel(SmsGateway gateway, String events, PhoneDirectory phones) { this(gateway, events, phones, SmsLog.inMemory()); }

    @Override public String name() { return "SMS"; }
    @Override public boolean accepts(Notification notification) { return events.contains(notification.type()); }

    @Override
    public void send(Notification n) {
        String text = "BITSolve: " + n.title() + ". " + n.message();
        String phone = phones.phoneOf(n.recipientId()).filter(number -> !number.isBlank()).orElse(null);
        if (phone == null) {
            history.record(new SentSms(Instant.now(), n.recipientId(), null, null, text, null, Status.NO_NUMBER));
            return;
        }
        try {
            String receipt = gateway.send(phone, text);
            history.record(new SentSms(Instant.now(), n.recipientId(), null, phone, text, receipt, Status.SENT));
        } catch (ExternalServiceException ex) {
            log.warn("SMS to user {} failed: {}", n.recipientId(), ex.getMessage());
            history.record(new SentSms(Instant.now(), n.recipientId(), null, phone, text, null, Status.FAILED));
        }
    }

    public List<SentSms> recent() { return history.recent(HISTORY); }
}
