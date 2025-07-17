package org.opennms.plugins.servicenow;

import org.junit.Test;
import org.opennms.plugins.servicenow.client.ApiClient;
import org.opennms.plugins.servicenow.client.ApiClientCredentials;
import org.opennms.plugins.servicenow.model.Alert;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.isNotNull;

public class ApiClientIT {


    @Test
    public void getAccessTokenAndSendTestAlarm() throws IOException, InterruptedException {
        ApiClient client = new ApiClient(ApiClientCredentials.builder()
                .withUrl("https://api.example.it")
                .withIgnoreSslCertificateValidation(false)
                .withUsername("client_id")
                .withPassword("client_secret")
                .build());
        client.getAccessToken();
        System.out.println(client.getToken());

        client.sendAlert(getTestAlert(Alert.Severity.MAJOR, Alert.Status.DOWN));

        Thread.sleep(1000);

        client.sendAlert(getTestAlert(Alert.Severity.NORMAL, Alert.Status.UP));

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
