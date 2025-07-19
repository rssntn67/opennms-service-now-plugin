package org.opennms.plugins.servicenow;

import org.junit.Test;
import org.opennms.plugins.servicenow.client.ApiClient;
import org.opennms.plugins.servicenow.client.ApiClientCredentials;
import org.opennms.plugins.servicenow.client.ApiClientProvider;
import org.opennms.plugins.servicenow.client.ApiClientProviderImpl;
import org.opennms.plugins.servicenow.client.ApiException;
import org.opennms.plugins.servicenow.client.ClientManager;
import org.opennms.plugins.servicenow.connection.Connection;
import org.opennms.plugins.servicenow.connection.ConnectionValidationError;
import org.opennms.plugins.servicenow.model.Alert;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ApiClientIT {

    public static final String URL = "https://api.example.it";
    public static final String USERNAME = "user";
    public static final String PASSWORD = "pass";

    public static ApiClientCredentials getCredentials() {
        return ApiClientCredentials.builder()
                .withUrl(URL)
                .withIgnoreSslCertificateValidation(false)
                .withUsername(USERNAME)
                .withPassword(PASSWORD)
                .build();
    }

    @Test
    public void getAccessTokenAndSendTestAlarm() throws InterruptedException, ApiException {
        ApiClient client = new ApiClient(getCredentials());
        System.out.println("AccessToken: " + client.getToken());

        Alert down = getTestAlert(Alert.Severity.MAJOR, Alert.Status.DOWN);
        System.out.println("sending:" + down);
        client.sendAlert(down);

        System.out.println("sleep");
        Thread.sleep(5000);
        System.out.println("slept");

        Alert up = getTestAlert(Alert.Severity.NORMAL, Alert.Status.UP);
        System.out.println("sending:" + up);
        client.sendAlert(up);

        client.check();
        System.out.println("AccessToken: " + client.getToken());

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

    @Test
    public void canPassFromConnectionToCredentials() {
        ApiClientCredentials credentialsA = ClientManager.asApiClientCredentials(new ConnectionTest());
        ApiClientCredentials credentialsB = ApiClientIT.getCredentials();
        assertThat(credentialsA, equalTo(credentialsB));
    }

    @Test
    public void canUseConnection() throws ApiException {
        // Wire it up
        ApiClientProvider apiClientProvider = new ApiClientProviderImpl();
        ClientManager clientManager = new ClientManager(apiClientProvider);
        Optional<ConnectionValidationError> validated = clientManager.validate((new ConnectionTest()));
        assertThat(validated.isEmpty(), is(true));
        ApiClient client = apiClientProvider.client(ApiClientIT.getCredentials());
        System.out.println("accessToken: " + client.getToken().getAccessToken());
        System.out.println("expiresIn: " + client.getToken().getExpires_in());
        System.out.println("refreshToken: " + client.getToken().getRefreshToken());
        System.out.println("tokenType: " + client.getToken().getTokenType());
        System.out.println("scope: " + client.getToken().getScope());

    }

    public static class ConnectionTest implements Connection {

        @Override
        public boolean isIgnoreSslCertificateValidation() {
            return false;
        }

        @Override
        public void setIgnoreSslCertificateValidation(boolean ignoreSslCertificateValidation) {

        }

        @Override
        public String getUrl() {
            return URL;
        }

        @Override
        public void setUrl(String url) {

        }

        @Override
        public String getUsername() {
            return USERNAME;
        }

        @Override
        public void setUsername(String username) {

        }

        @Override
        public String getPassword() {
            return PASSWORD;
        }

        @Override
        public void setPassword(String password) {

        }

        @Override
        public void save() {

        }

        @Override
        public void delete() {

        }
    }

}
