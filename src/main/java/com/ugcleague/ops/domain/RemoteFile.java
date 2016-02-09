package com.ugcleague.ops.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Objects;

@Entity
@Table(name = "remote_file",
    uniqueConstraints = @UniqueConstraint(columnNames = {"game_server_id", "folder", "filename"}))
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class RemoteFile extends AbstractAuditingEntity implements Comparable<RemoteFile> {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "game_server_id", nullable = false)
    private GameServer owner;

    @NotNull
    @Column(name = "folder", nullable = false)
    private String folder;

    @NotNull
    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "modified")
    private ZonedDateTime modified;

    @Column(name = "size")
    private Long size;

    @Column(name = "shared_url")
    private String sharedUrl;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public GameServer getOwner() {
        return owner;
    }

    public void setOwner(GameServer owner) {
        this.owner = owner;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public ZonedDateTime getModified() {
        return modified;
    }

    public void setModified(ZonedDateTime modified) {
        this.modified = modified;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getSharedUrl() {
        return sharedUrl;
    }

    public void setSharedUrl(String sharedUrl) {
        this.sharedUrl = sharedUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemoteFile that = (RemoteFile) o;
        return Objects.equals(id, that.id) &&
            Objects.equals(owner, that.owner) &&
            Objects.equals(folder, that.folder) &&
            Objects.equals(filename, that.filename);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, owner, folder, filename);
    }

    @Override
    public String toString() {
        return "RemoteFile{" +
            "id=" + id +
            ", owner=" + owner +
            ", folder='" + folder + '\'' +
            ", filename='" + filename + '\'' +
            ", modified=" + modified +
            ", size=" + size +
            ", sharedUrl='" + sharedUrl + '\'' +
            '}';
    }

    @Override
    public int compareTo(RemoteFile o) {
        return Comparator.nullsLast(Comparator.comparing(RemoteFile::getModified).reversed())
            .thenComparing(Comparator.comparingInt(RemoteFile::hashCode)).compare(this, o);
    }
}
