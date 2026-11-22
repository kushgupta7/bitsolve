package com.bits.itsm.notification.sms;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Record of every SMS attempt, shown in the admin's SMS outbox. */
public interface SmsLog {
    void record(SmsChannel.SentSms sms);
    List<SmsChannel.SentSms> recent(int limit);

    /** In-memory log for unit tests. */
    static SmsLog inMemory() {
        ConcurrentLinkedDeque<SmsChannel.SentSms> entries = new ConcurrentLinkedDeque<>();
        return new SmsLog() {
            public void record(SmsChannel.SentSms sms) { entries.addFirst(sms); }
            public List<SmsChannel.SentSms> recent(int limit) { return entries.stream().limit(limit).toList(); }
        };
    }
}
