package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;

import javax.validation.constraints.NotNull;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class UgcWeek {

    @Id
    private Integer id;

    @NotNull
    private Set<UgcResult> results = new LinkedHashSet<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Set<UgcResult> getResults() {
        return results;
    }

    public void setResults(Set<UgcResult> results) {
        this.results = results;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UgcWeek ugcWeek = (UgcWeek) o;
        return Objects.equals(id, ugcWeek.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UgcWeek{" +
            "id=" + id +
            ", results=" + results +
            '}';
    }
}
