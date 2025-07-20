package app.foxochat.dto.api.response;

import app.foxochat.model.Message;
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

    private Long id;

    private String content;

    private MemberDTO author;

    private Long authorId;

    private ChannelShortDTO channel;

    private Long channelId;

    private List<AttachmentDTO> attachments;

    private Long createdAt;

    public MessageDTO(Message message, boolean withChannel, boolean withAuthor, boolean withAttachments) {
        this.id = message.getId();
        this.content = message.getContent();
        if (withAuthor) this.author = new MemberDTO(message.getAuthor(), false, false, false);
        this.authorId = message.getAuthor().getId();
        if (withChannel) this.channel = new ChannelShortDTO(message.getChannel(), null, false, false, false);
        this.channelId = message.getChannel().getId();
        if (message.getAttachments() != null && withAttachments) this.attachments = message.getAttachments().stream()
                .map(messageAttachment -> new AttachmentDTO(messageAttachment.getAttachment()))
                .collect(Collectors.toList());
        else this.attachments = new ArrayList<>();
        this.createdAt = message.getTimestamp();
    }
}
