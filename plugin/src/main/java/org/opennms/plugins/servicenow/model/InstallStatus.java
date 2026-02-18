package org.opennms.plugins.servicenow.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

@JsonSerialize(using = InstallStatus.Serializer.class)
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

}