package org.opennms.plugins.servicenow.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

@JsonSerialize(using = TipoCollegamento.Serializer.class)
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

}