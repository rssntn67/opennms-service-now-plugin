package org.opennms.plugins.servicenow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONException;
import org.junit.Test;
import org.opennms.plugins.servicenow.model.Alert;
import org.skyscreamer.jsonassert.JSONAssert;

public class AlertTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Verifies that the object is serialized to JSON as expected.
     */
    @Test
    public void canSerializeToJson() throws JsonProcessingException, JSONException {
        Alert alert = new Alert();
        alert.setStatus(Alert.Status.DOWN);
        alert.setDescription("CPU is above upper limit (91%)");

        String expectedJson = "{\n" +
                "  \"status\": \"0\",\n" +
                "  \"description\": \"CPU is above upper limit (91%)\",\n" +
                "}";
        JSONAssert.assertEquals(expectedJson, mapper.writeValueAsString(alert), false);
    }
}
