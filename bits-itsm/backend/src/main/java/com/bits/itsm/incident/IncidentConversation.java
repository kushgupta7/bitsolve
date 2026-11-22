package com.bits.itsm.incident;

import com.bits.itsm.user.*;
import com.bits.itsm.event.IncidentEventPublisher;
import java.util.List;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service @Transactional
public class IncidentConversation {
    private final IncidentRepository incidents;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final IncidentEventPublisher events;
    public IncidentConversation(IncidentRepository incidents, UserRepository users, JdbcTemplate jdbc, IncidentEventPublisher events) {
        this.incidents=incidents; this.users=users; this.jdbc=jdbc; this.events=events;
    }
    private User actor(String email) { return users.findByEmail(email).filter(User::isEnabled).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)); }
    private Incident authorized(Long id, User user) {
        var incident=incidents.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean allowed=user.getRole()==Role.ADMIN || user.getRole()==Role.STUDENT && incident.getReporter().getId().equals(user.getId())
                || user.getRole()==Role.TECHNICIAN && incident.participates(user.getId());
        if(!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You do not have access to this conversation.");
        return incident;
    }
    public record Entry(Long id, String sender, Long senderId, String content, Instant createdAt, boolean unread) { }
    public record Conversation(List<Entry> messages, long unreadCount, boolean canSend) { }
    public Conversation list(Long id,String email) {
        var user=actor(email); var incident=authorized(id,user);
        Long read=jdbc.query("select last_read_message_id from incident_message_reads where incident_id=? and user_id=?",rs -> rs.next()?rs.getLong(1):0L,id,user.getId());
        var messages=incident.getNotes().stream().filter(n -> n.getKind()==InvestigationNote.Kind.MESSAGE && n.getVisibility()==InvestigationNote.Visibility.STUDENT_VISIBLE)
                .map(n -> new Entry(n.getId(),n.getAuthor().getName(),n.getAuthor().getId(),n.getContent(),n.getCreatedAt(),n.getId()>read && !n.getAuthor().getId().equals(user.getId()))).toList();
        return new Conversation(messages,messages.stream().filter(Entry::unread).count(),incident.getAssignedTechnician()!=null && incident.getStatus()!=IncidentStatus.CLOSED);
    }
    public Conversation send(Long id,String content,String email) {
        var user=actor(email); var incident=authorized(id,user);
        if(incident.getAssignedTechnician()==null || incident.getStatus()==IncidentStatus.CLOSED) throw new IllegalStateException("Messaging is available after assignment and before closure.");
        incident.addNote(user,content,InvestigationNote.Visibility.STUDENT_VISIBLE,InvestigationNote.Kind.MESSAGE);
        incident.record(user,"MESSAGE_SENT","Sent a message to the incident conversation.",InvestigationNote.Visibility.STUDENT_VISIBLE);
        incidents.flush();
        events.publish(id,user.getId(),"MESSAGE_SENT","A new message is available in the incident conversation.");
        return list(id,email);
    }
    public void read(Long id,Long messageId,String email) {
        var user=actor(email); var incident=authorized(id,user);
        if(messageId==null || incident.getNotes().stream().noneMatch(n -> n.getId().equals(messageId) && n.getKind()==InvestigationNote.Kind.MESSAGE))
            throw new IllegalArgumentException("Select a message from this conversation.");
        jdbc.update("insert into incident_message_reads(incident_id,user_id,last_read_message_id) values(?,?,?) on conflict(incident_id,user_id) do update set last_read_message_id=greatest(incident_message_reads.last_read_message_id,excluded.last_read_message_id)",id,user.getId(),messageId);
    }
}
