package app.foxochat.dto.api.response;

import app.foxochat.model.Message;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(name = "Messages")
public class MessagesDTO {

    private List<MessageDTO> messages;

    public MessagesDTO(List<Message> messages) {
        for (Message message : messages) {
            this.messages.add(new MessageDTO(message, true, false, false, false));
        }
    }
}
