package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Objects;

@Document(collection = "tag")
public class Tag extends AbstractAuditingEntity {

    @Id
    private String id;
    @DBRef
    private DiscordUser author;
    @DBRef
    private DiscordGuild guild = null;
    private String content;
    @Indexed(sparse = true)
    private String parent = null; // for aliases
    private boolean direct = false; // use without .tag
    private boolean global = true;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DiscordUser getAuthor() {
        return author;
    }

    public void setAuthor(DiscordUser author) {
        this.author = author;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    public boolean isDirect() {
        return direct;
    }

    public void setDirect(boolean direct) {
        this.direct = direct;
    }

    public DiscordGuild getGuild() {
        return guild;
    }

    public void setGuild(DiscordGuild guild) {
        this.guild = guild;
    }

    public boolean isGlobal() {
        return global;
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tag that = (Tag) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Tag{" +
            "id='" + id + '\'' +
            ", author=" + author +
            ", guild=" + guild +
            ", content='" + content + '\'' +
            ", parent='" + parent + '\'' +
            ", direct=" + direct +
            ", global=" + global +
            '}';
    }
}
