package com.ugcleague.ops.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Objects;

/**
 * A ServerFile.
 */
@Entity
@Table(name = "server_file")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ServerFile implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @NotNull
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @NotNull
    @Column(name = "remote_url", nullable = false)
    private String remoteUrl;

    @NotNull
    @Column(name = "required", nullable = false)
    private Boolean required;

    @Column(name = "last_modified")
    private Long lastModified;

    @Column(name = "e_tag")
    private String eTag;

    @ManyToOne
    @JoinColumn(name = "sync_group_id")
    private SyncGroup syncGroup;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Long getLastModified() {
        return lastModified;
    }

    public void setLastModified(Long lastModified) {
        this.lastModified = lastModified;
    }

    public String geteTag() {
        return eTag;
    }

    public void seteTag(String eTag) {
        this.eTag = eTag;
    }

    public SyncGroup getSyncGroup() {
        return syncGroup;
    }

    public void setSyncGroup(SyncGroup syncGroup) {
        this.syncGroup = syncGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ServerFile serverFile = (ServerFile) o;
        return Objects.equals(id, serverFile.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "ServerFile{" + "id=" + id + ", name='" + name + "'" + ", remoteUrl='" + remoteUrl + "'" + ", required='"
            + required + "'" + ", lastModified='" + lastModified + "'" + ", eTag='" + eTag + "'" + '}';
    }
}
