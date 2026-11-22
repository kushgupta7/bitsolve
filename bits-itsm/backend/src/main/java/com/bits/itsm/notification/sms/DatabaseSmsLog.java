package com.bits.itsm.notification.sms;

import java.sql.Timestamp;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** SMS log in PostgreSQL (table sms_messages, V9), so the outbox survives restarts. */
@Component
public class DatabaseSmsLog implements SmsLog {
    private static final Logger log = LoggerFactory.getLogger(DatabaseSmsLog.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate separate;

    public DatabaseSmsLog(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        // Own transaction: a logging problem must never roll back the incident change that triggered the SMS.
        this.separate = new TransactionTemplate(manager);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void record(SmsChannel.SentSms sms) {
        try {
            separate.executeWithoutResult(status -> jdbc.update(
                    "insert into sms_messages(sent_at, recipient_id, phone, body, receipt, status) values (?,?,?,?,?,?)",
                    Timestamp.from(sms.at()), sms.recipientId(), sms.phone(), truncate(sms.text()), sms.receipt(), sms.status().name()));
        } catch (RuntimeException e) {
            log.warn("Could not record SMS to user {}: {}", sms.recipientId(), e.getMessage());
        }
    }

    @Override
    public List<SmsChannel.SentSms> recent(int limit) {
        return jdbc.query("select s.sent_at, s.recipient_id, u.name, s.phone, s.body, s.receipt, s.status from sms_messages s "
                + "left join app_users u on u.id = s.recipient_id order by s.sent_at desc, s.id desc limit ?",
                (rs, row) -> new SmsChannel.SentSms(rs.getTimestamp(1).toInstant(), rs.getObject(2, Long.class), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), SmsChannel.Status.valueOf(rs.getString(7))), limit);
    }

    private static String truncate(String text) { return text == null ? "" : text.length() <= 1000 ? text : text.substring(0, 1000); }
}
