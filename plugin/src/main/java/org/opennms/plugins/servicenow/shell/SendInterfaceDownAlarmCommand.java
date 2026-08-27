package org.opennms.plugins.servicenow.shell;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.integration.api.v1.model.Severity;
import org.opennms.integration.api.v1.model.immutables.ImmutableAlarm;
import org.opennms.integration.api.v1.model.immutables.ImmutableMetaData;
import org.opennms.integration.api.v1.model.immutables.ImmutableNode;
import org.opennms.integration.api.v1.model.immutables.ImmutableNodeAssetRecord;
import org.opennms.plugins.servicenow.AlarmForwarder;

import java.util.Date;
import java.util.List;

@Command(scope = "opennms-service-now", name = "send-interface-down-alarm", description = "Send Test Alarm Interface Down.")
@Service
public class SendInterfaceDownAlarmCommand implements Action {

    @Reference
    private AlarmForwarder forwarder;

    @Argument(name = "alarmId", description = "Alarm Id", required = true)
    public int alarmId = -1000;

    @Argument(index = 1, name = "nodeId", description = "nodeId of the asset", required = true)
    public int nodeId = -1;

    @Argument(index = 2, name = "label", description = "label of the asset", required = true)
    public String nodeLabel = "TestLabel";

    @Argument(index = 3, name = "parentLabel", description = "label  of parent of the asset", required = true)
    public String parentLabel = "parentTestLabel";

    @Argument(index = 4, name = "ipaddr", description = "ip address of the asset", required = true)
    public String ipaddr = "10.10.10.10";

    @Override
    public Object execute() {
        forwarder.handleNewOrUpdatedAlarm(getAlarm(this.alarmId, this.nodeId, this.nodeLabel, this.parentLabel, this.ipaddr));
        return null;
    }

    public static Alarm getAlarm(int alarmId, int nodeId, String nodeLabel, String parentLabel, String ipaddr) {
        return ImmutableAlarm.newBuilder()
                .setId(alarmId)
                .setReductionKey(AlarmForwarder.ALARM_UEI_INTERFACE_DOWN+"::"+nodeId+":"+ipaddr)
                .setSeverity(Severity.MINOR)
                .setDescription("<p>All services are down on interface "+ipaddr +
                        " </p> " +
                        "<p>" +
                        "This event is generated when node outage" +
                        " processing determines that the critical service or all" +
                        " services on the interface are now down " +
                        "</p> " +
                        "<p>" +
                        "New outage records have been created and service level" +
                        " availability calculations will be impacted until this outage" +
                        " is resolved." +
                        "</p>")
                .setLogMessage("Interface "+ipaddr+" is down.")
                .setFirstEventTime(new Date())
                .setLastEventTime(new Date())
                .setNode(ImmutableNode.newBuilder()
                        .setId(nodeId)
                        .setLocation("Asia")
                        .setLabel(nodeLabel)
                        .setCategories(List.of("CategoryA", "CategoryB", "Minnovo","MinnovoTest"))
                        .addMetaData(ImmutableMetaData.newBuilder().setContext("requisition")
                                .setKey("parent")
                                .setValue(parentLabel).build())
                        .setAssetRecord(ImmutableNodeAssetRecord.newBuilder()
                                .setDescription("AssetRecord.Description")
                                .build())
                        .build()
                ).build();

    }

}
