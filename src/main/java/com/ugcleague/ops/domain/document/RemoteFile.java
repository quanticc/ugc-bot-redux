package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Objects;

@Document(collection = "remote_file")
public class RemoteFile extends AbstractAuditingEntity implements Comparable<RemoteFile> {

    @Id
    private String id;

    private String server;

    private String folder;

    private String filename;

    private ZonedDateTime modified;

    private Long size;

    @Field("shared_url")
    private String sharedUrl;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
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
            Objects.equals(server, that.server) &&
            Objects.equals(folder, that.folder) &&
            Objects.equals(filename, that.filename);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, server, folder, filename);
    }

    @Override
    public String toString() {
        return "RemoteFile{" +
            "id=" + id +
            ", server=" + server +
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
