package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Objects;

@Document(collection = "sound_bite")
public class SoundBite {

    @Id
    private String id;

    private String path;

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
}
