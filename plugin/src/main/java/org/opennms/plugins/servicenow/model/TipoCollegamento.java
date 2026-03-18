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

@JsonSerialize(using = TipoCollegamento.Serializer.class)
@JsonDeserialize(using = TipoCollegamento.Deserializer.class)
public enum TipoCollegamento {
    CAMPUS("campus"),
    SCTT("sctt"),
    ALTRO("altro");

    private final String text;

    TipoCollegamento(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static class Serializer extends StdSerializer<TipoCollegamento> {
        protected Serializer() {
            super(TipoCollegamento.class);
        }

        @Override
        public void serialize(TipoCollegamento tipo, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeString(tipo.getText());
        }
    }

    public static class Deserializer extends StdDeserializer<TipoCollegamento> {
        protected Deserializer() {
            super(TipoCollegamento.class);
        }

        @Override
        public TipoCollegamento deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            for (TipoCollegamento tipo : TipoCollegamento.values()) {
                if (tipo.getText().equals(value)) {
                    return tipo;
                }
            }
            throw new IOException("Cannot deserialize TipoCollegamento from value: " + value);
        }
    }

}