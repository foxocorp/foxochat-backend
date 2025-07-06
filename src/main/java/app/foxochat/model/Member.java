package app.foxochat.model;

import app.foxochat.constant.MemberConstant;
import app.foxochat.exception.member.MissingPermissionsException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "members", indexes = {
        @Index(name = "idx_member_user_channel", columnList = "user_id, channel_id")
})
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public long id;

    @Column
    public long permissions;

    @Column
    public long joinedAt;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    public Member() {
    }

    public Member(User user, Channel channel, long permissions) {
        this.user = user;
        this.channel = channel;
        this.permissions = permissions;
        this.joinedAt = System.currentTimeMillis();
    }

    public void addPermission(MemberConstant.Permissions permission) {
        this.permissions |= permission.getBit();
    }

    public void addPermissions(MemberConstant.Permissions... permissions) {
        for (MemberConstant.Permissions permission : permissions) {
            this.permissions |= permission.getBit();
        }
    }

    public void setPermissions(MemberConstant.Permissions... permissions) {
        this.permissions = 0;
        for (MemberConstant.Permissions permission : permissions) {
            this.permissions |= permission.getBit();
        }
    }

    public void removePermission(MemberConstant.Permissions permission) {
        this.permissions &= ~permission.getBit();
    }

    public boolean hasPermission(MemberConstant.Permissions permission) {
        return (this.permissions & permission.getBit()) != 0;
    }

    public void hasPermissions(MemberConstant.Permissions... permissions) throws MissingPermissionsException {
        for (MemberConstant.Permissions permission : permissions) {
            if ((this.permissions & permission.getBit()) == 0) {
                return;
            }
        }

        throw new MissingPermissionsException();
    }

    public boolean hasAnyPermission(MemberConstant.Permissions... permissions) {
        for (MemberConstant.Permissions permission : permissions) {
            if ((this.permissions & permission.getBit()) != 0) {
                return true;
            }
        }

        return false;
    }
}
