package org.opennms.plugins.servicenow;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;
import org.opennms.integration.api.v1.events.EventForwarder;
import org.opennms.plugins.servicenow.client.ApiClientProvider;
import org.opennms.plugins.servicenow.client.ApiClientProviderImpl;
import org.opennms.plugins.servicenow.connection.Connection;
import org.opennms.plugins.servicenow.connection.ConnectionManager;
import org.opennms.plugins.servicenow.model.TokenResponse;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AlarmForwarderIT {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule();

    @Test
    public void canForwardAlarm() {
        // Wire it up
        EventForwarder eventForwarder = mock(EventForwarder.class);
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        ApiClientProvider apiClientProvider = new ApiClientProviderImpl();
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken("accessToken");
        apiClientProvider.setToken(tokenResponse);
        AlarmForwarder alarmForwarder = new AlarmForwarder(connectionManager,apiClientProvider, eventForwarder, "CategoryA");

        when(connectionManager.getConnection()).thenReturn(Optional.of(new ConnectionTest()));

        // Stub the endpoint
        stubFor(post((urlEqualTo("//crea_aggiorna_allarmi")))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Handle some alarm
        alarmForwarder.handleNewOrUpdatedAlarm(AlarmForwarderTest.getAlarm());

        // Verify that the call was made
        await().atMost(15, TimeUnit.SECONDS)
                .catchUncaughtExceptions()
                .until(() -> {
                    verify(1, postRequestedFor(urlPathEqualTo("//crea_aggiorna_allarmi")));
                    return true;
                });
    }

    private class ConnectionTest implements Connection {

        @Override
        public boolean isIgnoreSslCertificateValidation() {
            return true;
        }

        @Override
        public void setIgnoreSslCertificateValidation(boolean ignoreSslCertificateValidation) {

        }

        @Override
        public String getUrl() {
            return wireMockRule.url("/");
        }

        @Override
        public void setUrl(String url) {

        }

        @Override
        public String getUsername() {
            return "username";
        }

        @Override
        public void setUsername(String username) {

        }

        @Override
        public String getPassword() {
            return "password";
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
