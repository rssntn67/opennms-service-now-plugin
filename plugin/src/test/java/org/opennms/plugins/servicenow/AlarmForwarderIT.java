package org.opennms.plugins.servicenow;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;
import org.opennms.integration.api.v1.events.EventForwarder;
import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.integration.api.v1.model.Severity;
import org.opennms.integration.api.v1.model.immutables.ImmutableAlarm;
import org.opennms.plugins.servicenow.client.ApiClientCredentials;
import org.opennms.plugins.servicenow.client.ApiClientProvider;
import org.opennms.plugins.servicenow.connection.ConnectionManager;

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

public class AlarmForwarderIT {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule();

    @Test
    public void canForwardAlarm() {
        // Wire it up
        ApiClientCredentials apiClientCredentials = ApiClientCredentials.builder()
                .withUrl(wireMockRule.url("/"))
                .withUsername("username")
                .withPassword("password")
                .withIgnoreSslCertificateValidation(true)
                .build();
        EventForwarder eventForwarder = mock(EventForwarder.class);
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        ApiClientProvider apiClientProvider = mock(ApiClientProvider.class);
        AlarmForwarder alarmForwarder = new AlarmForwarder(connectionManager,apiClientProvider, eventForwarder, "Minnovo");

        // Stub the endpoint
        stubFor(post((urlEqualTo("/data/v2/alerts")))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Handle some alarm
        Alarm alarm = ImmutableAlarm.newBuilder()
                .setId(1)
                .setReductionKey("hey:oh")
                .setSeverity(Severity.CRITICAL)
                .build();
        alarmForwarder.handleNewOrUpdatedAlarm(alarm);

        // Verify that the call was made
        await().atMost(15, TimeUnit.SECONDS)
                .catchUncaughtExceptions()
                .until(() -> {
                    verify(1, postRequestedFor(urlPathEqualTo("/data/v2/alerts")));
                    return true;
                });
    }
}
