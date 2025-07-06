package app.foxochat.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "attachments", indexes = {
        @Index(name = "idx_attachment_id", columnList = "id", unique = true),
        @Index(name = "idx_attachment_user_id", columnList = "id, user_id")
})
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column
    private String uuid;

    @Column
    private String filename;

    @Column
    private String contentType;

    @Column
    private long flags;

    @Column
    private String tumbhash;

    public Attachment() {
    }

    public Attachment(User user, String uuid, String filename, String contentType, long flags) {
        this.user = user;
        this.uuid = uuid;
        this.filename = filename;
        this.contentType = contentType;
        this.flags = flags;
    }
}
