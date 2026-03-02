package org.opennms.plugins.servicenow.shell;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.table.Col;
import org.apache.karaf.shell.support.table.ShellTable;
import org.opennms.integration.api.v1.dao.NodeDao;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.plugins.servicenow.AssetForwarder;

import java.util.HashMap;
import java.util.Map;

@Command(scope = "opennms-service-now", name = "get-asset-cache", description = "Print the asset cache.")
@Service
public class GetAssetCacheCommand implements Action {

    @Reference
    private Session session;

    @Reference
    private AssetForwarder forwarder;

    @Reference
    private NodeDao nodeDao;

    @Override
    public Object execute() {
        final var table = new ShellTable()
                .size(session.getTerminal().getWidth() - 1)
                .column(new Col("ForeignSource"))
                .column(new Col("ForeignId"))
                .column(new Col("Label"))
                .column(new Col("NodeId"))
                .column(new Col("ParentLabel"));

        Map<String, Node> nodeIndex = new HashMap<>();
        for (Node n : nodeDao.getNodes()) {
            nodeIndex.put(n.getForeignSource() + "::" + n.getForeignId(), n);
        }

        forwarder.getNetworkDeviceCache().forEach((assetTag, json) -> {
            var nd = forwarder.toNetworkDevice(json);
            if (nd == null) return;
            String[] parts = assetTag.split("::", 2);
            String fs = parts.length > 0 ? parts[0] : "";
            String fid = parts.length > 1 ? parts[1] : "";
            Node node = nodeIndex.get(assetTag);
            String nodeId = node != null ? String.valueOf(node.getId()) : "";
            final var row = table.addRow();
            row.addContent(fs);
            row.addContent(fid);
            row.addContent(nd.getName());
            row.addContent(nodeId);
            row.addContent(nd.getParentalNode());
        });

        forwarder.getAccessPointCache().forEach((assetTag, json) -> {
            var ap = forwarder.toAccessPoint(json);
            if (ap == null) return;
            String[] parts = assetTag.split("::", 2);
            String fs = parts.length > 0 ? parts[0] : "";
            String fid = parts.length > 1 ? parts[1] : "";
            Node node = nodeIndex.get(assetTag);
            String nodeId = node != null ? String.valueOf(node.getId()) : "";
            final var row = table.addRow();
            row.addContent(fs);
            row.addContent(fid);
            row.addContent(ap.getName());
            row.addContent(nodeId);
            row.addContent(ap.getParentalNode());
        });

        table.print(System.out, true);
        return null;
    }
}