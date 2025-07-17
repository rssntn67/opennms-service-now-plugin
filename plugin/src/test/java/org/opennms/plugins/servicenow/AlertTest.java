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
        alert.setSource("Test");
        alert.setType("opennms network alarm");
        alert.setSeverity(Alert.Severity.WARNING);
        alert.setMaintenance(false);
        alert.setDescription("CPU is above upper limit (91%)");
        alert.setMetricName("MetricName");
        alert.setKey("LogMessage");
        alert.setResource("100");
        alert.setNode("Node");
        alert.setAsset("Asset");
        alert.setAlertTags("AlertTags");
        alert.setStatus(Alert.Status.DOWN);

        String expectedJson = "{\n" +
                "  \"source\": \"Test\",\n" +
                "  \"type\": \"opennms network alarm\",\n" +
                "  \"maintenance\": false,\n" +
                "  \"severity\": \"1\",\n" +
                "  \"description\": \"CPU is above upper limit (91%)\",\n" +
                "  \"metric_name\": \"MetricName\",\n" +
                "  \"message_key\": \"LogMessage\",\n" +
                "  \"resource\": \"100\",\n" +
                "  \"node\": \"Node\",\n" +
                "  \"asset\": \"Asset\",\n" +
                "  \"alert_tags\": \"AlertTags\",\n" +
                "  \"status\": \"1\"\n" +
                "}";
        JSONAssert.assertEquals(expectedJson, mapper.writeValueAsString(alert), false);
    }
}
