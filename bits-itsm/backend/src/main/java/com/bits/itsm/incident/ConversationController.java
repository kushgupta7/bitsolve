package com.bits.itsm.incident;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/incidents/{id}/messages")
public class ConversationController {
    private final IncidentConversation conversations;
    public ConversationController(IncidentConversation conversations) { this.conversations=conversations; }
    @GetMapping public IncidentConversation.Conversation list(@PathVariable Long id,Authentication auth) { return conversations.list(id,auth.getName()); }
    @PostMapping public IncidentConversation.Conversation send(@PathVariable Long id,@Valid @RequestBody IncidentData.Message message,Authentication auth) { return conversations.send(id,message.content(),auth.getName()); }
    public record Read(@NotNull Long messageId) { }
    @PostMapping("/read") public java.util.Map<String,Boolean> read(@PathVariable Long id,@Valid @RequestBody Read read,Authentication auth) {
        conversations.read(id,read.messageId(),auth.getName()); return java.util.Map.of("read",true);
    }
}
