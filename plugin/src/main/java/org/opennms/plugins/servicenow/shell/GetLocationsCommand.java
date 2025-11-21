package org.opennms.plugins.servicenow.shell;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.table.Col;
import org.apache.karaf.shell.support.table.ShellTable;
import org.opennms.plugins.servicenow.EdgeService;

@Command(scope = "opennms-service-now", name = "get-locations", description = "Get List of Onms locations.")
@Service
public class GetLocationsCommand implements Action {

    @Reference
    private Session session;

    @Reference
    private EdgeService service;

    @Override
    public Object execute() {
        final var table = new ShellTable()
                .size(session.getTerminal().getWidth() - 1)
                .column(new Col("location").maxSize(72));
        for (String location: service.getLocations()) {
            final var row = table.addRow();
            row.addContent(location);
        }
        table.print(System.out,true);
        return null;
    }
}
