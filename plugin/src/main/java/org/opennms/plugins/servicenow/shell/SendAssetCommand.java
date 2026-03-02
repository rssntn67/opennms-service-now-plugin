package org.opennms.plugins.servicenow.shell;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.integration.api.v1.model.immutables.ImmutableGeolocation;
import org.opennms.integration.api.v1.model.immutables.ImmutableMetaData;
import org.opennms.integration.api.v1.model.immutables.ImmutableNode;
import org.opennms.integration.api.v1.model.immutables.ImmutableNodeAssetRecord;
import org.opennms.plugins.servicenow.AssetForwarder;
import org.opennms.plugins.servicenow.model.TipoApparato;

import java.util.List;

@Command(scope = "opennms-service-now", name = "send-asset", description = "Send Test Asset.")
@Service
public class SendAssetCommand implements Action {

    @Reference
    private AssetForwarder forwarder;

    @Argument(name = "foreignSource", description = "Foreign Source of the Asset", required = true)
    public String foreignSource = "Test";

    @Argument(index = 1, name = "foreignId", description = "Foreign Id of the asset", required = true)
    public int foreignId = 1234;

    @Argument(index = 2, name = "label", description = "Label of the asset", required = true)
    public String nodeLabel = "testLabel";

    @Argument(index = 3, name = "parentLabel", description = "Label of the parent node", required = true)
    public String parentLabel = "parentTestLabel";

    @Argument(index = 4, name = "location", description = "Location of the asset: Default, sctt, or other", required = true)
    public String location = "Default";

    @Argument(index = 5, name = "ipAddress", description = "IP address of the asset", required = true)
    public String ipAddress = "10.0.0.1";

    @Argument(index = 6, name = "category", description = "Type of asset: Wifi, Switch, Firewall, ModemLte, ModemXdsl", required = true)
    public String category = "Switch";

    @Override
    public Object execute() {
        Node node = getNode(foreignSource, foreignId, nodeLabel, parentLabel, location, category);
        switch (category) {
            case "Wifi":
                forwarder.sendAccessPoint(node, AssetForwarder.toAccessPoint(node, parentLabel, ipAddress));
                break;
            case "Switch":
                forwarder.sendNetworkDevice(node, AssetForwarder.toNetworkDevice(node, parentLabel, ipAddress, TipoApparato.SWITCH));
                break;
            case "Firewall":
                forwarder.sendNetworkDevice(node, AssetForwarder.toNetworkDevice(node, parentLabel, ipAddress, TipoApparato.FIREWALL));
                break;
            case "ModemLte":
                forwarder.sendNetworkDevice(node, AssetForwarder.toNetworkDevice(node, parentLabel, ipAddress, TipoApparato.MODEM_LTE));
                break;
            case "ModemXdsl":
                forwarder.sendNetworkDevice(node, AssetForwarder.toNetworkDevice(node, parentLabel, ipAddress, TipoApparato.MODEM_XDSL));
                break;
            default:
                System.out.println("Unknown category: " + category + ". Valid values: Wifi, Switch, Firewall, ModemLte, ModemXdsl");
        }
        return null;
    }

    public static Node getNode(String fs, int fid, String nodeLabel, String parentLabel, String location, String category) {
        return ImmutableNode.newBuilder()
                .setId(fid)
                .setForeignSource(fs)
                .setForeignId("" + fid)
                .setLocation(location)
                .setLabel(nodeLabel)
                .setCategories(List.of("Minnovo", category))
                .addMetaData(ImmutableMetaData.newBuilder()
                        .setContext("requisition")
                        .setKey("parent")
                        .setValue(parentLabel)
                        .build())
                .setAssetRecord(ImmutableNodeAssetRecord.newBuilder()
                        .setDescription("Milano")
                        .setVendor("TestVendor")
                        .setModelNumber("TestModel")
                        .setOperatingSystem("TestModelId")
                        .setAssetNumber("TestSerial")
                        .setGeolocation(ImmutableGeolocation.newBuilder()
                                .setLatitude(45.4642)
                                .setLongitude(9.1900)
                                .build())
                        .build())
                .build();
    }
}