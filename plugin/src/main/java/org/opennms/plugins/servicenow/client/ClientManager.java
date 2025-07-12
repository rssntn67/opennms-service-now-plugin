
package org.opennms.plugins.servicenow.client;

import org.opennms.plugins.servicenow.connection.Connection;
import org.opennms.plugins.servicenow.connection.ConnectionValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

public class ClientManager {
    private static final Logger LOG = LoggerFactory.getLogger(ClientManager.class);

    private final ApiClientProvider clientProvider;

    public ClientManager(ApiClientProvider provider) {
        clientProvider = Objects.requireNonNull(provider);
    }

    public Optional<ConnectionValidationError> validate(Connection connection) {
        try {
            clientProvider.client(asApiClientCredentials(connection));
            boolean validated = clientProvider.validate();
            LOG.info("validate: {}", validated);
            if (validated) {
                return Optional.empty();
            }
        } catch (ApiException e) {
            LOG.error("validate: {} failed", connection);
        }
        return Optional.of(new ConnectionValidationError("Connection could not be validated"));
    }

    public static ApiClientCredentials asApiClientCredentials(Connection connection) {
        return ApiClientCredentials.builder()
                .withUsername(connection.getUsername())
                .withPassword(connection.getPassword())
                .withUrl(connection.getUrl())
                .withIgnoreSslCertificateValidation(connection.isIgnoreSslCertificateValidation())
                .build();
    }

}
