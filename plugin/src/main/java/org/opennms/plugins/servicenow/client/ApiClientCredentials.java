package org.opennms.plugins.servicenow.client;

import java.util.Objects;

/**
 * Credentials for a Nutanix API connection.
 */
public class ApiClientCredentials {
    /**
     * The URL of the Service Now End point.
     */
    public final String url;

    /**
     * The username used to authenticate the connection to the Service Now.
     */
    public final String username;

    /**
     * The password used to authenticate the connection to the Service Now.
     */
    public final String password;

    /**
     * Wheter to check SSL Certificate
     */
    public final Boolean ignoreSslCertificateValidation;

    private ApiClientCredentials(final Builder builder) {
        this.url = Objects.requireNonNull(builder.url);
        this.username = builder.username;
        this.password = builder.password;
        this.ignoreSslCertificateValidation = builder.ignoreSslCertificateValidation;
    }

    public static class Builder {
        private String url;
        private String username;
        private String password;

        private boolean ignoreSslCertificateValidation = false;

        private Builder() {
        }

        public Builder withUrl(final String url) {
            this.url = url;
            return this;
        }

        public Builder withUsername(final String username) {
            this.username = username;
            return this;
        }


        public Builder withPassword(final String password) {
            this.password = password;
            return this;
        }

        public Builder withIgnoreSslCertificateValidation(final Boolean ignoreSslCertificateValidation) {
            this.ignoreSslCertificateValidation = ignoreSslCertificateValidation;
            return this;
        }

        public ApiClientCredentials build() {
            return new ApiClientCredentials(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ApiClientCredentials credentials) {
        return builder()
                .withUrl(credentials.url)
                .withUsername(credentials.username)
                .withPassword(credentials.password)
                .withIgnoreSslCertificateValidation(credentials.ignoreSslCertificateValidation);

    }

    @Override
    public String toString() {
        return "ApiClientCredentials{" +
                "url='" + url + '\'' +
                ", username='" + username + '\'' +
                ", password='****" + '\'' +
                ", ignoreSslCertificateValidation=" + ignoreSslCertificateValidation +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApiClientCredentials)) {
            return false;
        }
        final ApiClientCredentials that = (ApiClientCredentials) o;
        return Objects.equals(this.url, that.url) &&
                Objects.equals(this.username, that.username) &&
                Objects.equals(this.password, that.password) &&
                Objects.equals(this.ignoreSslCertificateValidation, that.ignoreSslCertificateValidation);

    }

    @Override
    public int hashCode() {
        return Objects.hash(this.url,
                 this.username, this.password, this.ignoreSslCertificateValidation);
    }

}
