package org.opennms.plugins.servicenow.shell;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.table.Col;
import org.apache.karaf.shell.support.table.ShellTable;
import org.opennms.plugins.servicenow.AssetForwarder;

@Command(scope = "opennms-service-now", name = "get-asset-cache", description = "Print the asset cache.")
@Service
public class GetAssetCacheCommand implements Action {

    @Reference
    private Session session;

    @Reference
    private AssetForwarder forwarder;

    @Override
    public Object execute() {
        final var table = new ShellTable()
                .size(session.getTerminal().getWidth() - 1)
                .column(new Col("ForeignSource"))
                .column(new Col("ForeignId"))
                .column(new Col("Label"))
                .column(new Col("Type"))
                .column(new Col("Detail"))
                .column(new Col("ParentLabel"));

        forwarder.getNetworkDeviceCache().forEach((assetTag, json) -> {
            var nd = forwarder.toNetworkDevice(json);
            if (nd == null) return;
            final var row = table.addRow();
            row.addContent(AssetForwarder.getForeignSourceFromAssetTag(assetTag));
            row.addContent(AssetForwarder.getForeignIdFromAssetTag(assetTag));
            row.addContent(nd.getName());
            row.addContent("NetworkDevice");
            row.addContent(nd.getTipoApparato() != null ? nd.getTipoApparato().name() : "");
            row.addContent(nd.getParentalNode());
        });

        forwarder.getAccessPointCache().forEach((assetTag, json) -> {
            var ap = forwarder.toAccessPoint(json);
            if (ap == null) return;
            final var row = table.addRow();
            row.addContent(AssetForwarder.getForeignSourceFromAssetTag(assetTag));
            row.addContent(AssetForwarder.getForeignIdFromAssetTag(assetTag));
            row.addContent(ap.getName());
            row.addContent("AccessPoint");
            row.addContent(ap.getTipoCollegamento() != null ? ap.getTipoCollegamento().name() : "");
            row.addContent(ap.getParentalNode());
        });

        table.print(System.out, true);
        return null;
    }
}
