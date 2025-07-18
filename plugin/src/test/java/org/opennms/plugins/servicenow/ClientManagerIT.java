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

import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.opennms.plugins.servicenow.ApiClientIT.PASSWORD;
import static org.opennms.plugins.servicenow.ApiClientIT.URL;
import static org.opennms.plugins.servicenow.ApiClientIT.USERNAME;

public class ClientManagerIT {

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
