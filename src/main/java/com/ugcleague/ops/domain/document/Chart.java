package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Document(collection = "chart")
public class Chart extends AbstractAuditingEntity {

    @Id
    private String id;
    @Indexed(unique = true)
    private String name;
    private String title;
    private String xAxisLabel = "";
    private String yAxisLabel = "";
    @DBRef
    private Set<Series> seriesList = new LinkedHashSet<>();

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

    public String getXAxisLabel() {
        return xAxisLabel;
    }

    public void setXAxisLabel(String xAxisLabel) {
        this.xAxisLabel = xAxisLabel;
    }

    public String getYAxisLabel() {
        return yAxisLabel;
    }

    public void setYAxisLabel(String yAxisLabel) {
        this.yAxisLabel = yAxisLabel;
    }

    public Set<Series> getSeriesList() {
        return seriesList;
    }

    public void setSeriesList(Set<Series> seriesList) {
        this.seriesList = seriesList;
    }

    @Override
    public String toString() {
        return "Chart{" +
            "name='" + name + '\'' +
            "title='" + title + '\'' +
            "xLabel='" + xAxisLabel + '\'' +
            "yLabel='" + yAxisLabel + '\'' +
            ", seriesList=" + seriesList +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chart chart = (Chart) o;
        return Objects.equals(id, chart.id) &&
            Objects.equals(name, chart.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}
