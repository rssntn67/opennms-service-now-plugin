package org.opennms.plugins.servicenow;

import org.junit.Test;
import org.opennms.plugins.servicenow.client.ApiClient;
import org.opennms.plugins.servicenow.client.ApiClientCredentials;
import org.opennms.plugins.servicenow.client.ApiException;
import org.opennms.plugins.servicenow.model.Alert;

public class ApiClientIT {

    public static ApiClientCredentials getCredentials() {
        return ApiClientCredentials.builder()
                .withUrl("https://api.example.it")
                .withIgnoreSslCertificateValidation(false)
                .withUsername("user")
                .withPassword("pass")
                .build();
    }

    @Test
    public void getAccessTokenAndSendTestAlarm() throws InterruptedException, ApiException {
        ApiClient client = new ApiClient(getCredentials());
        System.out.println("Bearer: " + client.getToken());

        Alert down = getTestAlert(Alert.Severity.MAJOR, Alert.Status.DOWN);
        System.out.println("sending:" + down);
        client.sendAlert(down);

        System.out.println("sleep");
        Thread.sleep(5000);
        System.out.println("slept");

        Alert up = getTestAlert(Alert.Severity.NORMAL, Alert.Status.UP);
        System.out.println("sending:" + up);
        client.sendAlert(up);

        client.getAccessToken();
        System.out.println("Bearer: " + client.getToken());

    }

    private static Alert getTestAlert(Alert.Severity severity, Alert.Status status) {
        Alert alert = new Alert();
        alert.setSource("ApiClientIT");
        alert.setType("opennms network alarm");
        alert.setSeverity(severity);
        alert.setMaintenance(false);
        alert.setDescription("TEST Description");
        alert.setMetricName("ReductionKey+Id");
        alert.setKey("TEST Message Key");
        alert.setResource("Id");
        alert.setNode("Hostname");
        alert.setAsset("AssetRecord.Description");
        alert.setAlertTags("Test,Minnovo,OpenNMS.PlugIn");
        alert.setStatus(status);
        return alert;
    }
}
