package org.opennms.plugins.servicenow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Ignore;
import org.junit.Test;
import org.opennms.plugins.servicenow.client.ApiClient;
import org.opennms.plugins.servicenow.client.ApiClientCredentials;
import org.opennms.plugins.servicenow.client.ApiClientProviderImpl;
import org.opennms.plugins.servicenow.client.ApiException;
import org.opennms.plugins.servicenow.client.ClientManager;
import org.opennms.plugins.servicenow.connection.Connection;
import org.opennms.plugins.servicenow.connection.ConnectionManager;
import org.opennms.plugins.servicenow.connection.ConnectionValidationError;
import org.opennms.plugins.servicenow.model.Alert;
import org.opennms.plugins.servicenow.model.TokenResponse;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    private static final String TOKEN_END_POINT = "token";
    private static final String ALERT_END_POINT = "minnovo/a2a/servicenow/opennms/1.0/sespa/comi_opennms/crea_aggiorna_allarmi";
    private static final String ASSET_END_POINT = "minnovo/a2a/servicenow/opennms/1.0/sespa/comi_opennms/inserisci_aggiorna_asset";

    @Test
    @Ignore
    public void getAccessTokenAndSendTestAlarm() throws InterruptedException, ApiException {
        ApiClient client = new ApiClient();
        TokenResponse token = client.getAccessToken(getCredentials(), TOKEN_END_POINT);
        System.out.println("AccessToken: " + token);

        Alert down = getTestAlert(Alert.Severity.MAJOR, Alert.Status.DOWN);
        System.out.println("sending:" + down);
        client.sendAlert(down, getCredentials(), token.getAccessToken(), ALERT_END_POINT);

        System.out.println("sleep");
        Thread.sleep(5000);
        System.out.println("slept");

        Alert up = getTestAlert(Alert.Severity.CLEAR, Alert.Status.UP);
        System.out.println("sending:" + up);
        client.sendAlert(up, getCredentials(), token.getAccessToken(), ALERT_END_POINT);

    }

    private static Alert getTestAlert(Alert.Severity severity, Alert.Status status) {
        Alert alert = new Alert();
        alert.setId("6060");
        alert.setTime(new Date());
        alert.setSource("ApiClientIT");
        alert.setType("opennms network alarm");
        alert.setSeverity(severity);
        alert.setMaintenance(false);
        alert.setDescription("TEST Description");
        alert.setMetricName("ReductionKey+Id");
        alert.setKey("TEST Message Key");
        alert.setResource("Id");
        alert.setNode("Hostname");
        alert.setAsset("50");
        alert.setAlertTags(List.of("Test","Minnovo","OpenNMS.PlugIn").toString());
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
    @Ignore
    public void canUseConnectionAndForwardAlarm() {
        // Wire it up
        ApiClientProviderImpl apiClientProvider = new ApiClientProviderImpl(TOKEN_END_POINT, ALERT_END_POINT, ASSET_END_POINT);
        ClientManager clientManager = new ClientManager(apiClientProvider);
        Optional<ConnectionValidationError> validated = clientManager.validate((new ConnectionTest()));
        assertThat(validated.isEmpty(), is(true));
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        EdgeService service = mock(EdgeService.class);
        org.opennms.integration.api.v1.events.EventForwarder eventForwarder = mock(org.opennms.integration.api.v1.events.EventForwarder.class);
        AlarmForwarder alarmForwarder = new AlarmForwarder(connectionManager, apiClientProvider, "CategoryA", service, eventForwarder);
        when(connectionManager.getConnection()).thenReturn(Optional.of(new ConnectionTest()));
        alarmForwarder.handleNewOrUpdatedAlarm(AlarmForwarderTest.getAlarm());
        System.out.println("accessToken: " + apiClientProvider.getTokenResponse().getAccessToken());
        System.out.println("expiresIn: " + apiClientProvider.getTokenResponse().getExpires_in());
        System.out.println("refreshToken: " + apiClientProvider.getTokenResponse().getRefreshToken());
        System.out.println("tokenType: " + apiClientProvider.getTokenResponse().getTokenType());
        System.out.println("scope: " + apiClientProvider.getTokenResponse().getScope());
        System.out.println("expireAt: " + apiClientProvider.getExpiresAt());


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
