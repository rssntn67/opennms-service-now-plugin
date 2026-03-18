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

@JsonSerialize(using = TipoApparato.Serializer.class)
@JsonDeserialize(using = TipoApparato.Deserializer.class)
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

    public static class Deserializer extends StdDeserializer<TipoApparato> {
        protected Deserializer() {
            super(TipoApparato.class);
        }

        @Override
        public TipoApparato deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            for (TipoApparato tipo : TipoApparato.values()) {
                if (tipo.getText().equals(value)) {
                    return tipo;
                }
            }
            throw new IOException("Cannot deserialize TipoApparato from value: " + value);
        }
    }

}
