package org.opennms.plugins.servicenow.connection;

import org.opennms.integration.api.v1.runtime.Container;
import org.opennms.integration.api.v1.runtime.RuntimeInfo;
import org.opennms.integration.api.v1.scv.Credentials;
import org.opennms.integration.api.v1.scv.SecureCredentialsVault;
import org.opennms.integration.api.v1.scv.immutables.ImmutableCredentials;
import org.opennms.plugins.servicenow.client.ApiClientCredentials;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ConnectionManager {

    private static final String PREFIX = "servicenow_connection_";
    public static final String URL_KEY = "url";
    public static final String IGNORE_SSL_CERTIFICATE_VALIDATION_KEY = "ignoreSslCertificateValidation";

    public static final  String alias = "Default";
    private final RuntimeInfo runtimeInfo;
    private final SecureCredentialsVault vault;

    public ConnectionManager(final RuntimeInfo runtimeInfo,
                             final SecureCredentialsVault vault) {
        this.runtimeInfo = Objects.requireNonNull(runtimeInfo);
        this.vault = Objects.requireNonNull(vault);
    }

    /**
     * Returns a connection config for the given alias.
     *
     * @return The connection config or {@code Optional#empty()} of no such alias exists
     */
    public Optional<Connection> getConnection() {
        this.ensureCore();

        final var credentials = this.vault.getCredentials(PREFIX + alias);
        if (credentials == null) {
            return Optional.empty();
        }
        ConnectionImpl conn = new ConnectionImpl(fromStore(credentials));
        return Optional.of(conn);
    }

    /**
     * Creates a basic authentication connection under the given alias.
     *
     * @param url        the URL of the prism server
     * @param username          the username to authenticate the connection
     * @param password          the password to authenticate the connection
     * @param ignoreSslCerticateValidation          ignore Ssl Certificate Validation
     */
    public Connection newConnection(final String url, final String username, final String password, final boolean ignoreSslCerticateValidation) {
        this.ensureCore();

        return new ConnectionImpl(ApiClientCredentials.builder()
                .withUrl(url)
                .withUsername(username)
                .withPassword(password)
                .withIgnoreSslCertificateValidation(ignoreSslCerticateValidation)
                .build());
    }

    /**
     * Deletes a connection under the given alias.
     *
     * @return <b>true</b> if an existing connection with given alias was found and deleted and <b>false</b> if no
     * connection with given alias was not found
     */
    public boolean deleteConnection() {
        this.ensureCore();

        final var connection = this.getConnection();
        if (connection.isEmpty()) {
            return false;
        }
        connection.get().delete();
        return true;
    }

    private static boolean isNullOrEmpty(String check) {
        return check == null || check.isEmpty();

    }

    private static ApiClientCredentials fromStore(final Credentials credentials) {

        if (isNullOrEmpty(credentials.getPassword())) {
            throw new IllegalStateException("API password is missing");
        }
        if (isNullOrEmpty(credentials.getUsername())) {
            throw new IllegalStateException("API username is missing");
        }

        if (isNullOrEmpty(credentials.getAttribute(URL_KEY))) {
            throw new IllegalStateException("URL is missing");
        }

        if (isNullOrEmpty(credentials.getAttribute(IGNORE_SSL_CERTIFICATE_VALIDATION_KEY))) {
            throw new IllegalStateException("Ignore  SSL CERTIFICATION Validation is missing");
        }


        final var cUrl = credentials.getAttribute(URL_KEY);

        final var username = credentials.getUsername();
        final var password = credentials.getPassword();
        final var ignoreSslCertificateValidation = Boolean.parseBoolean(credentials.getAttribute(IGNORE_SSL_CERTIFICATE_VALIDATION_KEY));
        return ApiClientCredentials.builder()
                .withUrl(cUrl)
                .withUsername(username)
                .withPassword(password)
                .withIgnoreSslCertificateValidation(ignoreSslCertificateValidation)
                .build();
        }


    private class ConnectionImpl implements Connection {
        private ApiClientCredentials credentials;

        private ConnectionImpl(final ApiClientCredentials credentials) {
            this.credentials = Objects.requireNonNull(credentials);
        }

        @Override
        public boolean isIgnoreSslCertificateValidation() {
            return this.credentials.ignoreSslCertificateValidation;
        }

        @Override
        public void setIgnoreSslCertificateValidation(boolean ignoreSslCertificateValidation) {
            this.credentials = ApiClientCredentials.builder(this.credentials)
                    .withIgnoreSslCertificateValidation(ignoreSslCertificateValidation)
                    .build();
        }

        @Override
        public String getUrl() {
            return this.credentials.url;
        }

        @Override
        public void setUrl(final String url) {
            this.credentials = ApiClientCredentials.builder()
                                                            .withUrl(url)
                                                            .build();
        }
        @Override
        public String getUsername() {
            return this.credentials.username;
        }

        @Override
        public void setUsername(final String username) {
            this.credentials = ApiClientCredentials.builder(this.credentials)
                    .withUsername(username)
                    .build();
        }

        @Override
        public String getPassword() {
            return this.credentials.password;
        }

        @Override
        public void setPassword(final String password) {
            this.credentials = ApiClientCredentials.builder(this.credentials)
                    .withPassword(password)
                    .build();
        }

        @Override
        public void save() {
            ConnectionManager.this.vault.setCredentials(PREFIX + ConnectionManager.alias, this.asCredentials());
        }

        @Override
        public void delete() {
            ConnectionManager.this.vault.deleteCredentials(PREFIX + ConnectionManager.alias);
        }

        private Credentials asCredentials() {
            Map<String,String> credentialMap = new HashMap<>();
            credentialMap.put(URL_KEY, this.credentials.url);
            credentialMap.put(IGNORE_SSL_CERTIFICATE_VALIDATION_KEY, String.valueOf(this.credentials.ignoreSslCertificateValidation));
            return new ImmutableCredentials(this.credentials.username, this.credentials.password, credentialMap);
        }

        @Override
        public String toString() {
            return "ConnectionImpl{" +
                    "credentials=" + credentials +
                    '}';
        }
    }

    public void ensureCore() {
        if (this.runtimeInfo.getContainer() != Container.OPENNMS) {
            throw new IllegalStateException("Operation only allowed on OpenNMS instance");
        }
    }
}
