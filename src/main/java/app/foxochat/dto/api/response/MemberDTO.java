package app.foxochat.dto.api.response;

import app.foxochat.model.Member;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "Member")
public class MemberDTO {

    private long id;

    private UserShortDTO user;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ChannelShortDTO channel;

    private long permissions;

    private long joinedAt;

    public MemberDTO(Member member, boolean withChannel, boolean withUser, boolean withOwner) {
        this.id = member.getId();
        if (withUser) this.user = new UserShortDTO(member.getUser(), true, true);
        if (withChannel) this.channel = new ChannelShortDTO(member.getChannel(), null, false, false, withOwner);
        this.permissions = member.getPermissions();
        this.joinedAt = member.getJoinedAt();
    }
}

