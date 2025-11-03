package org.opennms.plugins.servicenow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;
import org.opennms.integration.api.v1.model.Node;
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
import static org.opennms.plugins.servicenow.client.ApiClient.ALERT_END_POINT;
import static org.opennms.plugins.servicenow.client.ApiClient.TOKEN_END_POINT;

public class AlarmForwarderIT {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule();

    @Test
    public void canForwardAlarm() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        // Wire it up
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        EdgeService service = mock(EdgeService.class);
        ApiClientProvider apiClientProvider = new ApiClientProviderImpl();
        AlarmForwarder alarmForwarder = new AlarmForwarder(connectionManager,apiClientProvider, "CategoryA", service);

        when(connectionManager.getConnection()).thenReturn(Optional.of(new ConnectionTest()));
        TokenResponse response = new TokenResponse();
        response.setAccessToken("accessToken");
        response.setExpires_in(3600);
        response.setScope("scope");
        response.setRefreshToken("refreshToken");
        response.setTokenType("TokenType");
        // Stub the endpoint
        System.out.println(mapper.writeValueAsString(response));
        stubFor(post((urlEqualTo("//"+TOKEN_END_POINT)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody((mapper.writeValueAsString(response))))
        );

        stubFor(
                post(
                    urlEqualTo("//"+ALERT_END_POINT))
                    .willReturn(aResponse()
                    .withStatus(200)
                )
        );

        alarmForwarder.handleNewOrUpdatedAlarm(AlarmForwarderTest.getAlarm());

        await().atMost(15, TimeUnit.SECONDS)
                .catchUncaughtExceptions()
                .until(() -> {
                    verify(1, postRequestedFor(urlPathEqualTo("//"+TOKEN_END_POINT)));
                    verify(1, postRequestedFor(urlPathEqualTo("//"+ALERT_END_POINT)));
                    return true;
                });


        // Handle some alarm
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
