package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.NotNull;
import java.util.Objects;

@Document(collection = "series")
public class Series extends AbstractAuditingEntity {

    @Id
    private String id;
    @Indexed(unique = true)
    private String name;
    private String title;
    @NotNull
    private String metric;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    @Override
    public String toString() {
        return "Series{" +
            "name='" + name + '\'' +
            "title='" + title + '\'' +
            ", metric='" + metric + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Series series = (Series) o;
        return Objects.equals(id, series.id) &&
            Objects.equals(name, series.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}
