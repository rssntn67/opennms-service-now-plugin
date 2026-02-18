package org.opennms.plugins.servicenow.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

@JsonSerialize(using = TipoApparato.Serializer.class)
public enum TipoApparato {
    SWITCH("switch"),
    MODEM_LTE("modem_lte"),
    MODEM_XDSL("modem_xdsl"),
    FIREWALL("firewall");

    private final String text;

    TipoApparato(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static class Serializer extends StdSerializer<TipoApparato> {
        protected Serializer() {
            super(TipoApparato.class);
        }

        @Override
        public void serialize(TipoApparato tipo, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeString(tipo.getText());
        }
    }

}
