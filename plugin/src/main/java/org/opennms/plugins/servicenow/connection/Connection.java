
package org.opennms.plugins.servicenow.connection;


public interface Connection {

    boolean isIgnoreSslCertificateValidation();

    void setIgnoreSslCertificateValidation(boolean ignoreSslCertificateValidation);

    /**
     * Returns the URL of the orchestrator.
     * @return the orchestrator URL
     */
    String getUrl();

    /**
     * Changes the URL of the orchestrator.
     * @param url the new URL
     */
    void setUrl(final String url);

    /**
     * Returns the Username used to authenticate the connection.
     * @return the username
     */
    String getUsername();

    /**
     * Changes the API username used to authenticate the connection.
     * @param username username
     */
    void setUsername(final String username);

    /**
     * Returns the password used to authenticate the connection.
     * @return the password
     */
    String getPassword();

    /**
     * Changes the API password used to authenticate the connection.
     * @param password password
     */
    void setPassword(final String password);

    /**
     * Save the altered connection config in the underlying store.
     */
    void save();

    void delete();

}
