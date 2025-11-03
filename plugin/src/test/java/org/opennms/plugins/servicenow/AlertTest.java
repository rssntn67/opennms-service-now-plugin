package org.opennms.plugins.servicenow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONException;
import org.junit.Test;
import org.opennms.plugins.servicenow.model.Alert;
import org.opennms.plugins.servicenow.model.TokenResponse;
import org.skyscreamer.jsonassert.JSONAssert;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AlertTest {

    private final static String response = "{\"access_token\":\"eyJ4NXQiOiJPV1E1TURkak9EVTFaamt6TnpSaE5UbGxNak5sWkdFME9UWXdZMlF3WXpoak1UZGlNbU16Tm1VeE1ESTVZamMyTlRnMVl6TmtPRGhoTmpBM05HWmlPQSIsImtpZCI6Ik9XUTVNRGRqT0RVMVpqa3pOelJoTlRsbE1qTmxaR0UwT1RZd1kyUXdZemhqTVRkaU1tTXpObVV4TURJNVlqYzJOVGcxWXpOa09EaGhOakEzTkdaaU9BX1JTMjU2IiwidHlwIjoiYXQrand0IiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJ2YWxlcmlvLm1hc2Nhcm8iLCJhdXQiOiJBUFBMSUNBVElPTiIsImF1ZCI6InRHWVRZVmRZSTNRYkNZVER3bDdLbFg2Tzljd2EiLCJiaW5kaW5nX3R5cGUiOiJyZXF1ZXN0IiwibmJmIjoxNzUyOTQ3ODY0LCJhenAiOiJ0R1lUWVZkWUkzUWJDWVREd2w3S2xYNk85Y3dhIiwiaXNzIjoiaHR0cHM6XC9cL2FwaWtzLmNvbXVuZS5taWxhbm8uaXQ6OTQ0NFwvb2F1dGgyXC90b2tlbiIsImV4cCI6MTc1Mjk1MTQ2NCwiaWF0IjoxNzUyOTQ3ODY0LCJiaW5kaW5nX3JlZiI6IjMyZjU3MDNlZjMwMmRkZjJlZTBkZDM2YjJhYjg1NDI3IiwianRpIjoiZjI3NjkwZDUtNmNhZi00YTBkLWIxYzgtOGM5NzcyMTg4ZmEwIiwiY2xpZW50X2lkIjoidEdZVFlWZFlJM1FiQ1lURHdsN0tsWDZPOWN3YSJ9.TTv0Xtc6qCqcNSb-qvgE8k6XexBE-yVvaLqCbXQmzMoB0TJkWtn9MW3gyYaC5unkl4WvDReOPJKWdHLvoURBB23REMaoBiv-F4TZuuT1uBiArj4EF4ZvDweKWgz5hgFabpWu4puFc7VY7y1_cFrksKqbeTbPRmzMeNN37EfGxPed-_PEPyb32LxT4UIHLmfPftrByJpLwejV2L0Z23RVQ1EjnySLL7vetXh91oMEPOaXkrB9Fp6QWggMGd_RKCnRO4ey237ddEjj3baKkteajmdvoG4TphjzQB1tPMLVAEz2NmEqYrXIKPJCkInHZGMW8cL1uUMex9H2SSotZM9uKg\",\"token_type\":\"Bearer\",\"expires_in\":3600}";

    private static Alert getAlert() {
        Alert alert = new Alert();
        alert.setId("200");
        alert.setTime(new Date());
        alert.setSource("Test");
        alert.setType("opennms network alarm");
        alert.setSeverity(Alert.Severity.WARNING);
        alert.setMaintenance(false);
        alert.setDescription("CPU is above upper limit (91%)");
        alert.setMetricName("MetricName");
        alert.setKey("LogMessage");
        alert.setResource("Resource");
        alert.setNode("Node");
        alert.setAsset("100");
        alert.setAlertTags("AlertTags");
        alert.setStatus(Alert.Status.DOWN);
        alert.setParentalNodeLabel("parentalNodeLabel");
        return alert;
    }

    /**
     * Verifies that the object is serialized to JSON as expected.
     */
    @Test
    public void canSerializeToJson() throws JsonProcessingException, JSONException {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.readValue(response, TokenResponse.class);

        Alert alert = getAlert();
        String pattern = "yyyy-MM-dd HH:mm:ss";
        DateFormat df = new SimpleDateFormat(pattern);
        String dateString = df.format(alert.getTime());
        System.out.println(dateString);
        String expectedJson = "{\n" +
                "  \"u_id_alert_opennms\": \"200\",\n" +
                "  \"sys_created_on\": \""+dateString+"\",\n" +
                "  \"source\": \"Test\",\n" +
                "  \"type\": \"opennms network alarm\",\n" +
                "  \"maintenance\": false,\n" +
                "  \"severity\": \"4\",\n" +
                "  \"description\": \"CPU is above upper limit (91%)\",\n" +
                "  \"metric_name\": \"MetricName\",\n" +
                "  \"message_key\": \"LogMessage\",\n" +
                "  \"resource\": \"Resource\",\n" +
                "  \"node\": \"Node\",\n" +
                "  \"cmdb_ci\": \"100\",\n" +
                "  \"alert_tags\": \"AlertTags\",\n" +
                "  \"status\": \"0\",\n" +
                "  \"u_parental_node_opennms\": \"parentalNodeLabel\"\n" +
                "}";
        JSONAssert.assertEquals(expectedJson, mapper.writeValueAsString(alert), false);
    }


}
