package org.opennms.plugins.servicenow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final static String response = "{\"access_token\":\"eyJ4NXQiOiJPV1E1TURkak9EVTFaamt6TnpSaE5UbGxNak5sWkdFME9UWXdZMlF3WXpoak1UZGlNbU16Tm1VeE1ESTVZamMyTlRnMVl6TmtPRGhoTmpBM05HWmlPQSIsImtpZCI6Ik9XUTVNRGRqT0RVMVpqa3pOelJoTlRsbE1qTmxaR0UwT1RZd1kyUXdZemhqTVRkaU1tTXpObVV4TURJNVlqYzJOVGcxWXpOa09EaGhOakEzTkdaaU9BX1JTMjU2IiwidHlwIjoiYXQrand0IiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJ2YWxlcmlvLm1hc2Nhcm8iLCJhdXQiOiJBUFBMSUNBVElPTiIsImF1ZCI6InRHWVRZVmRZSTNRYkNZVER3bDdLbFg2Tzljd2EiLCJiaW5kaW5nX3R5cGUiOiJyZXF1ZXN0IiwibmJmIjoxNzUyOTQ3ODY0LCJhenAiOiJ0R1lUWVZkWUkzUWJDWVREd2w3S2xYNk85Y3dhIiwiaXNzIjoiaHR0cHM6XC9cL2FwaWtzLmNvbXVuZS5taWxhbm8uaXQ6OTQ0NFwvb2F1dGgyXC90b2tlbiIsImV4cCI6MTc1Mjk1MTQ2NCwiaWF0IjoxNzUyOTQ3ODY0LCJiaW5kaW5nX3JlZiI6IjMyZjU3MDNlZjMwMmRkZjJlZTBkZDM2YjJhYjg1NDI3IiwianRpIjoiZjI3NjkwZDUtNmNhZi00YTBkLWIxYzgtOGM5NzcyMTg4ZmEwIiwiY2xpZW50X2lkIjoidEdZVFlWZFlJM1FiQ1lURHdsN0tsWDZPOWN3YSJ9.TTv0Xtc6qCqcNSb-qvgE8k6XexBE-yVvaLqCbXQmzMoB0TJkWtn9MW3gyYaC5unkl4WvDReOPJKWdHLvoURBB23REMaoBiv-F4TZuuT1uBiArj4EF4ZvDweKWgz5hgFabpWu4puFc7VY7y1_cFrksKqbeTbPRmzMeNN37EfGxPed-_PEPyb32LxT4UIHLmfPftrByJpLwejV2L0Z23RVQ1EjnySLL7vetXh91oMEPOaXkrB9Fp6QWggMGd_RKCnRO4ey237ddEjj3baKkteajmdvoG4TphjzQB1tPMLVAEz2NmEqYrXIKPJCkInHZGMW8cL1uUMex9H2SSotZM9uKg\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
    public static ApiClientCredentials getCredentials() {
        return ApiClientCredentials.builder()
                .withUrl(URL)
                .withIgnoreSslCertificateValidation(false)
                .withUsername(USERNAME)
                .withPassword(PASSWORD)
                .build();
    }

    @Test
    public void canParseResponseToken() throws JsonProcessingException {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.readValue(response, TokenResponse.class);
    }
    @Test
    public void getAccessTokenAndSendTestAlarm() throws InterruptedException, ApiException {
        ApiClient client = new ApiClient();
        TokenResponse token = client.getAccessToken(getCredentials());
        System.out.println("AccessToken: " + token);

        Alert down = getTestAlert(Alert.Severity.MAJOR, Alert.Status.DOWN);
        System.out.println("sending:" + down);
        client.sendAlert(down, getCredentials(), token.getAccessToken());

        System.out.println("sleep");
        Thread.sleep(5000);
        System.out.println("slept");

        Alert up = getTestAlert(Alert.Severity.CLEAR, Alert.Status.UP);
        System.out.println("sending:" + up);
        client.sendAlert(up, getCredentials(), token.getAccessToken());

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
    public void canUseConnectionAndForwardAlarm() {
        // Wire it up
        ApiClientProviderImpl apiClientProvider = new ApiClientProviderImpl();
        ClientManager clientManager = new ClientManager(apiClientProvider);
        Optional<ConnectionValidationError> validated = clientManager.validate((new ConnectionTest()));
        assertThat(validated.isEmpty(), is(true));
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        AlarmForwarder alarmForwarder = new AlarmForwarder(connectionManager, apiClientProvider, "CategoryA");
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
