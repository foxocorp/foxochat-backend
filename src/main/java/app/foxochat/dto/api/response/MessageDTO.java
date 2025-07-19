package app.foxochat.dto.api.response;

import app.foxochat.model.Message;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@Schema(name = "Message")
public class MessageDTO {

    private long id;

    private String content;

    private MemberDTO author;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ChannelShortDTO channel;

    private List<AttachmentDTO> attachments;

    private long createdAt;

    public MessageDTO(Message message, boolean withChannel, boolean withUser, boolean withAuthor, boolean withAttachments) {
        this.id = message.getId();
        this.content = message.getContent();
        if (withAuthor) this.author = new MemberDTO(message.getAuthor(), false, withUser);
        if (withChannel) this.channel = new ChannelShortDTO(message.getChannel(), null, false, false, false);
        if (message.getAttachments() != null && withAttachments) this.attachments = message.getAttachments().stream()
                .map(messageAttachment -> new AttachmentDTO(messageAttachment.getAttachment()))
                .collect(Collectors.toList());
        else this.attachments = new ArrayList<>();
        this.createdAt = message.getTimestamp();
    }
}
