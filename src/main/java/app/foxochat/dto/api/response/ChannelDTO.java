package app.foxochat.dto.api.response;

import app.foxochat.constant.ChannelConstant;
import app.foxochat.constant.MemberConstant;
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

	private int type;

	private long flags;

	private int memberCount;

	private UserDTO owner;

	private long createdAt;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private MessageDTO lastMessage;

	public ChannelDTO(Channel channel, Message lastMessage, String displayName, String username, Avatar avatar) {
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
		this.type = channel.getType();
		this.flags = channel.getFlags();
		if (channel.getMembers() != null && channel.getType() != ChannelConstant.Type.DM.getType()) {
			this.memberCount = channel.getMembers().size();
		}
		if (lastMessage != null) {
			this.lastMessage = new MessageDTO(lastMessage, false);
		}

		this.owner = new UserDTO(channel.getMembers().stream().filter(m -> m.hasPermission(MemberConstant.Permissions.OWNER)).findFirst().get().getUser(), null, null, false, false, false);
		this.createdAt = channel.getCreatedAt();
	}
}
