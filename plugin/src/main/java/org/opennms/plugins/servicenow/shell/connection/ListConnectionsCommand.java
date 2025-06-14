package org.opennms.plugins.servicenow.shell.connection;

import it.xeniaprogetti.cisco.ucs.plugin.connection.ConnectionManager;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.table.Col;
import org.apache.karaf.shell.support.table.ShellTable;

@Command(scope = "opennms-cucs", name = "connection-list", description = "List existing connections", detailedDescription = "List all existing connections for Cisco UCS Manager XML API")
@Service
public class ListConnectionsCommand implements Action {

    @Reference
    private Session session;

    @Reference
    private ConnectionManager connectionManager;

    @Override
    public Object execute() throws Exception {
        final var table = new ShellTable()
                .size(session.getTerminal().getWidth() - 1)
                .column(new Col("alias").maxSize(36))
                .column(new Col("url").maxSize(72))
                .column(new Col("username").maxSize(36))
                .column(new Col("password").maxSize(36))
                .column(new Col("ignoreSslVal").maxSize(12))
                .column(new Col("validity").maxSize(4))
                ;

        this.connectionManager.getAliases().stream()
                                      .map(alias -> this.connectionManager.getConnection(alias).orElseThrow())
                                      .forEach(connection -> {
                                          final var row = table.addRow();
                                          row.addContent(connection.getAlias());
                                          row.addContent(connection.getUrl());
                                          row.addContent(connection.getUsername());
                                          row.addContent("*****");
                                          row.addContent(connection.isIgnoreSslCertificateValidation());
                                          row.addContent(connection.getValidityTime());
                                      });

        table.print(System.out, true);

        return null;
    }
}
