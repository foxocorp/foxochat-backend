package app.foxochat.dto.api.response;

import app.foxochat.constant.ChannelConstant;
import app.foxochat.constant.MemberConstant;
import app.foxochat.exception.member.MemberNotFoundException;
import app.foxochat.model.Avatar;
import app.foxochat.model.Channel;
import app.foxochat.model.Message;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "Channel")
public class ChannelDTO {

    private long id;

    private String displayName;

    private String name;

    private AvatarDTO avatar;

    private AvatarDTO banner;

    private int type;

    private long flags;

    private int memberCount;

    private long ownerId;

    private long createdAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private long lastMessage;

    public ChannelDTO(Channel channel, Message lastMessage, String displayName, String username, Avatar avatar)
            throws MemberNotFoundException {
        this.id = channel.getId();
        if (channel.getType() == ChannelConstant.Type.DM.getType()) {
            this.displayName = displayName;
            this.name = username;
            this.avatar = new AvatarDTO(avatar);
        } else {
            this.displayName = channel.getDisplayName();
            this.name = channel.getName();
        }
        if (channel.getAvatar() != null) {
            this.avatar = new AvatarDTO(channel.getAvatar());
        }
        if (channel.getBanner() != null) {
            this.banner = new AvatarDTO(channel.getBanner());
        }
        this.type = channel.getType();
        this.flags = channel.getFlags();
        if (channel.getMembers() != null && channel.getType() != ChannelConstant.Type.DM.getType()) {
            this.memberCount = channel.getMembers().size();
            this.ownerId = channel.getMembers().stream()
                    .filter(m -> m.hasPermission(MemberConstant.Permissions.OWNER))
                    .findFirst().orElseThrow(MemberNotFoundException::new).getUser().getId();
        }
        if (lastMessage != null) {
            this.lastMessage = lastMessage.getId();
        }
        this.createdAt = channel.getCreatedAt();
    }
}
