package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.NotNull;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Document(collection = "ugc_season")
public class UgcSeason {

    @Id
    private Integer id;

    @NotNull
    private Set<UgcWeek> weeks = new LinkedHashSet<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Set<UgcWeek> getWeeks() {
        return weeks;
    }

    public void setWeeks(Set<UgcWeek> weeks) {
        this.weeks = weeks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UgcSeason ugcSeason = (UgcSeason) o;
        return Objects.equals(id, ugcSeason.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UgcSeason{" +
            "id=" + id +
            ", weeks=" + weeks +
            '}';
    }
}
