package app.foxochat.dto.api.response;

import app.foxochat.constant.MemberConstant;
import app.foxochat.model.Channel;
import app.foxochat.model.Message;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "Channel")
public class ChannelShortDTO {

    private long id;

    private String displayName;

    private String name;

    private AvatarDTO avatar;

    private AvatarDTO banner;

    private UserShortDTO owner;

    private int memberCount;

    private int type;

    private long flags;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private MessageDTO lastMessage;

    public ChannelShortDTO(Channel channel, Message lastMessage, boolean withAvatar, boolean withBanner,
                           boolean withOwner) {
        this.id = channel.getId();
        this.displayName = channel.getDisplayName();
        this.name = channel.getName();
        if (channel.getAvatar() != null && withAvatar) {
            this.avatar = new AvatarDTO(channel.getAvatar());
        }
        if (channel.getBanner() != null && withBanner) {
            this.banner = new AvatarDTO(channel.getBanner());
        }
        this.type = channel.getType();
        this.flags = channel.getFlags();
        this.memberCount = channel.getMembers().size();
        if (lastMessage != null)
            this.lastMessage = new MessageDTO(lastMessage, false, false, true, false);
        if (withOwner) this.owner = new UserShortDTO(channel.getMembers().stream()
                .filter(m -> m.hasPermission(MemberConstant.Permissions.OWNER))
                .findFirst().get().getUser(), true, true);
    }
}
