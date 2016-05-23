package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Objects;

@Document(collection = "nuclear_stream")
public class NuclearStream extends AbstractAuditingEntity {

    @Id
    private String id;

    private String key;

    @DBRef
    private Publisher publisher;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Publisher getPublisher() {
        return publisher;
    }

    public void setPublisher(Publisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NuclearStream that = (NuclearStream) o;
        return Objects.equals(id, that.id) &&
            Objects.equals(key, that.key) &&
            Objects.equals(publisher, that.publisher);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, key, publisher);
    }

    @Override
    public String toString() {
        return "NuclearStream{" +
            "id='" + id + '\'' +
            ", key='" + key + '\'' +
            ", publisher=" + publisher +
            '}';
    }
}
