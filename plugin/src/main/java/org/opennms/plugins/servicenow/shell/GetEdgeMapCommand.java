package org.opennms.plugins.servicenow.shell;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.table.Col;
import org.apache.karaf.shell.support.table.ShellTable;
import org.opennms.integration.api.v1.model.TopologyProtocol;
import org.opennms.plugins.servicenow.EdgeService;

@Command(scope = "opennms-service-now", name = "get-edge-map", description = "Get Edge Map.")
@Service
public class GetEdgeMapCommand implements Action {

    @Reference
    private Session session;

    @Reference
    private EdgeService service;

    @Argument(name = "protocol", description = "lldp or bridge", required = true)
    public String protocol = null;

    @Argument(index = 1, name = "label", description = "label of the asset", required = true)
    public String label = null;

    @Override
    public Object execute() {
        final var table = new ShellTable()
                .size(session.getTerminal().getWidth() - 1)
                .column(new Col("label").maxSize(72))
                .column(new Col("linked to").maxSize(72))
                ;
        TopologyProtocol p = null;
        switch (this.protocol) {
            case "lldp":
                p= TopologyProtocol.LLDP;
                break;
            case "bridge":
                p=TopologyProtocol.BRIDGE;
                break;
            default:
                System.out.println(this.protocol + "not Supported");
                return null;
        }
        for (String entry : service.getEdges(p, label)) {
            final var row = table.addRow();
            row.addContent(this.label);
            row.addContent(entry);
        }
        table.print(System.out,true);
        return null;
    }

}
