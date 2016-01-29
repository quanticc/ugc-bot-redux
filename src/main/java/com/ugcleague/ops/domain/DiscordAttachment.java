package com.ugcleague.ops.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Objects;

/**
 * A DiscordAttachment.
 */
@Entity
@Table(name = "discord_attachment")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class DiscordAttachment implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @NotNull
    @Column(name = "discord_attachment_id", nullable = false, unique = true)
    private String discordAttachmentId;

    @Column(name = "filename")
    private String filename;

    @Column(name = "filesize")
    private Integer filesize;

    @Column(name = "url")
    private String url;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private DiscordMessage owner;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDiscordAttachmentId() {
        return discordAttachmentId;
    }

    public void setDiscordAttachmentId(String discordAttachmentId) {
        this.discordAttachmentId = discordAttachmentId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Integer getFilesize() {
        return filesize;
    }

    public void setFilesize(Integer filesize) {
        this.filesize = filesize;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public DiscordMessage getOwner() {
        return owner;
    }

    public void setOwner(DiscordMessage discordMessage) {
        this.owner = discordMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DiscordAttachment discordAttachment = (DiscordAttachment) o;
        return Objects.equals(id, discordAttachment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "DiscordAttachment{" +
            "id=" + id +
            ", discordAttachmentId='" + discordAttachmentId + "'" +
            ", filename='" + filename + "'" +
            ", filesize='" + filesize + "'" +
            ", url='" + url + "'" +
            '}';
    }
}
