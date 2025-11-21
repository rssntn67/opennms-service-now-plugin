package org.opennms.plugins.servicenow.shell;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.plugins.servicenow.EdgeService;

@Command(scope = "opennms-service-now", name = "get-gateway-label", description = "Get Gateway Label by ip address.")
@Service
public class GetGatewayLabelCommand implements Action {

    @Reference
    private EdgeService service;

    @Argument(name = "gateway", description = "ip address of the gateway", required = true)
    public String gateway = null;

    @Override
    public Object execute() {
        System.out.println(service.getGatewayLabel(this.gateway));
        return null;
    }
}
