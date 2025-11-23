package org.opennms.plugins.servicenow.shell;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.table.Col;
import org.apache.karaf.shell.support.table.ShellTable;
import org.opennms.plugins.servicenow.EdgeService;

import java.util.Map;

@Command(scope = "opennms-service-now", name = "get-parent-by-gateway-map", description = "Get Gateway Key Parent Node Data Map.")
@Service
public class GetParentByGatewayMapCommand implements Action {

    @Reference
    private Session session;

    @Reference
    private EdgeService service;

    @Override
    public Object execute() {
        final var table = new ShellTable()
                .size(session.getTerminal().getWidth() - 1)
                .column(new Col("label").maxSize(72))
                .column(new Col("parent").maxSize(72))
                ;
        for (Map.Entry<String,String> entry : service.getParentByGatewayKeyMap().entrySet()) {
            final var row = table.addRow();
            row.addContent(entry.getKey());
            row.addContent(entry.getValue());
        }
        table.print(System.out,true);
        return null;
    }

}
