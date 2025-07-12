package org.opennms.plugins.servicenow.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.Objects;

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.NONE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Alert {

    @JsonProperty("source")
    private String source;

    @JsonProperty("type")
    private String type;

    @JsonProperty("maintenance")
    private boolean maintenance;

    @JsonProperty("severity")
    @JsonSerialize(using = Severity.Serializer.class)
    private Severity severity;

    @JsonProperty("description")
    private String description;

    @JsonProperty("metric_name")
    private String metricName;

    @JsonProperty("message_key")
    private String key;

    @JsonProperty("resource")
    private String resource;

    @JsonProperty("node")
    private String node;

    @JsonProperty("asset")
    private String asset;

    @JsonProperty("alert_tags")
    private String alertTags;

    @JsonProperty("status")
    @JsonSerialize(using = Status.Serializer.class)
    private Status status;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isMaintenance() {
        return maintenance;
    }

    public void setMaintenance(boolean maintenance) {
        this.maintenance = maintenance;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getNode() {
        return node;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getAsset() {
        return asset;
    }

    public void setAsset(String asset) {
        this.asset = asset;
    }

    public String getAlertTags() {
        return alertTags;
    }

    public void setAlertTags(String alertTags) {
        this.alertTags = alertTags;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Alert alert = (Alert) o;
        return Objects.equals(status, alert.status) &&
                Objects.equals(key, alert.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, asset, description, key);
    }

    @Override
    public String toString() {
        return "Alert{" +
                "source='" + source + '\'' +
                ", type='" + type + '\'' +
                ", severity='" + severity + '\'' +
                ", description='" + description + '\'' +
                ", metricName='" + metricName + '\'' +
                ", key='" + key + '\'' +
                ", node='" + node + '\'' +
                ", asset='" + asset + '\'' +
                ", alertTags='" + alertTags + '\'' +
                ", status=" + status +
                '}';
    }

    public enum Severity {
        NORMAL("0"),
        WARNING("1"),
        MINOR("2"),
        MAJOR("3"),
        CRITICAL("4");

        private final String text;

        Severity(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public static class Serializer extends StdSerializer<Severity> {
            protected Serializer() {
                super(Severity.class);
            }

            @Override
            public void serialize(Severity severity, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeString(severity.getText());
            }
        }

    }

    public enum Status {
        UP("0"),
        DOWN("1");

        private final String text;

        Status(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public static class Serializer extends StdSerializer<Status> {
            protected Serializer() {
                super(Status.class);
            }

            @Override
            public void serialize(Status status, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                    jsonGenerator.writeString(status.getText());
            }
        }
    }

}
