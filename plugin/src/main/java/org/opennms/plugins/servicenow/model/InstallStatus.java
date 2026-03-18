package org.opennms.plugins.servicenow.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

@JsonSerialize(using = InstallStatus.Serializer.class)
@JsonDeserialize(using = InstallStatus.Deserializer.class)
public enum InstallStatus {
    ATTIVO("1"),
    DISATTIVO("7"),
    SOSPESO("100");

    private final String text;

    InstallStatus(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static class Serializer extends StdSerializer<InstallStatus> {
        protected Serializer() {
            super(InstallStatus.class);
        }

        @Override
        public void serialize(InstallStatus status, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeNumber(Integer.parseInt(status.getText()));
        }
    }

    public static class Deserializer extends StdDeserializer<InstallStatus> {
        protected Deserializer() {
            super(InstallStatus.class);
        }

        @Override
        public InstallStatus deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            for (InstallStatus status : InstallStatus.values()) {
                if (status.getText().equals(value)) {
                    return status;
                }
            }
            throw new IOException("Cannot deserialize InstallStatus from value: " + value);
        }
    }

}