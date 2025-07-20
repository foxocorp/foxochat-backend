package app.foxochat.dto.api.response;

import app.foxochat.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "User")
public class UserShortDTO {

    private long id;

    private AvatarDTO avatar;

    private AvatarDTO banner;

    private String displayName;

    private String username;

    private int status;

    private long statusUpdatedAt;

    private long createdAt;

    @SuppressWarnings("unused")
    public UserShortDTO() {
    }

    public UserShortDTO(User user, boolean withAvatar, boolean withBanner) {
        this.id = user.getId();
        if (user.getAvatar() != null && withAvatar) {
            this.avatar = new AvatarDTO(user.getAvatar());
        }
        if (user.getBanner() != null && withBanner) {
            this.banner = new AvatarDTO(user.getBanner());
        }
        this.displayName = user.getDisplayName();
        this.username = user.getUsername();
        this.status = user.getStatus();
        this.statusUpdatedAt = user.getStatusUpdatedAt();
        this.createdAt = user.getCreatedAt();
    }
}
