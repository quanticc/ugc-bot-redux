package com.ugcleague.ops.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ugcleague.ops.domain.enumeration.FileGroupType;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A SyncGroup.
 */
@Entity
@Table(name = "sync_group")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class SyncGroup implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @NotNull
    @Column(name = "local_dir", nullable = false, unique = true)
    private String localDir;

    @NotNull
    @Column(name = "remote_dir", nullable = false, unique = true)
    private String remoteDir;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private FileGroupType kind;

    @OneToMany(mappedBy = "syncGroup")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<ServerFile> serverFiles = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLocalDir() {
        return localDir;
    }

    public void setLocalDir(String localDir) {
        this.localDir = localDir;
    }

    public String getRemoteDir() {
        return remoteDir;
    }

    public void setRemoteDir(String remoteDir) {
        this.remoteDir = remoteDir;
    }

    public FileGroupType getKind() {
        return kind;
    }

    public void setKind(FileGroupType kind) {
        this.kind = kind;
    }

    public Set<ServerFile> getServerFiles() {
        return serverFiles;
    }

    public void setServerFiles(Set<ServerFile> serverFiles) {
        this.serverFiles = serverFiles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SyncGroup syncGroup = (SyncGroup) o;
        return Objects.equals(id, syncGroup.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "SyncGroup{" + "id=" + id + ", localDir='" + localDir + "'" + ", remoteDir='" + remoteDir + "'" + ", kind='" + kind
            + "'" + '}';
    }
}
