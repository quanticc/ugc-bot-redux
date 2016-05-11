package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Document(collection = "sound_bite")
public class SoundBite {

    @Id
    private String id;

    private String path;

    private PlaybackMode mode = PlaybackMode.SINGLE;

    private List<String> paths = new ArrayList<>();

    private Integer volume = 100;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public PlaybackMode getMode() {
        return mode;
    }

    public void setMode(PlaybackMode mode) {
        this.mode = mode;
    }

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public Integer getVolume() {
        return volume;
    }

    public void setVolume(Integer volume) {
        this.volume = volume;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SoundBite soundBite = (SoundBite) o;
        return Objects.equals(id, soundBite.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public enum PlaybackMode {
        SINGLE, SERIES, POOL, FOLDER
    }
}
