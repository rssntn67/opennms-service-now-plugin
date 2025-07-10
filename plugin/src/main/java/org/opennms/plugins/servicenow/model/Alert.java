package org.opennms.plugins.servicenow.model;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.NONE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Alert {

    @JsonProperty("status")
    @JsonSerialize(using = Status.Serializer.class)
    private Status status;

    @JsonProperty("description")
    private String description;

    @JsonProperty("asset")
    private String asset;

    @JsonProperty("message_key")
    private String key;


    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getAsset() {
        return asset;
    }

    public void setAsset(String asset) {
        this.asset = asset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Alert alert = (Alert) o;
        return Objects.equals(status, alert.status) &&
                Objects.equals(key, alert.key) &&
                Objects.equals(asset, alert.asset) &&
                Objects.equals(description, alert.description)
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, asset, description, key);
    }

    @Override
    public String toString() {
        return "Alert{" +
                "status='" + status + '\'' +
                ", asset='" + asset + '\'' +
                ", description='" + description + '\'' +
                ", key=" + key +
                '}';
    }


    public enum Status {
        UP,
        DOWN;

        public static class Serializer extends StdSerializer<Status> {
            protected Serializer() {
                super(Status.class);
            }

            @Override
            public void serialize(Status status, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                switch (status) {
                case UP:
                    jsonGenerator.writeString("1");
                    break;
                case DOWN:
                    jsonGenerator.writeString("0");
                    break;
            }
        }
    }

    public static class InstantSerializer extends StdSerializer<Instant> {
        protected InstantSerializer() {
            super(Instant.class);
        }

        @Override
        public void serialize(Instant instant, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeNumber(instant.getEpochSecond());
        }
    }
}
