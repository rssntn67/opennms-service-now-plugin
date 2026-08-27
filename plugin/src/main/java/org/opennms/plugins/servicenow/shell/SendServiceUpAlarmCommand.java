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

@Command(scope = "opennms-service-now", name = "send-service-up-alarm", description = "Send Test Alarm Service Up.")
@Service
public class SendServiceUpAlarmCommand implements Action {

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
                .setReductionKey(AlarmForwarder.ALARM_UEI_SERVICE_DOWN+"::"+nodeId+":"+ipaddr+":ICMP")
                .setSeverity(Severity.CLEARED)
                .setDescription("<p>The ICMP service on interface " + ipaddr + " was" +
                        " previously down and has been restored.</p>" +
                        " <p>This event is generated when a service which had" +
                        " previously failed polling attempts is again responding to" +
                        " polls by OpenNMS. </p> <p>This event will cause" +
                        " any active outages associated with this service/interface" +
                        " combination to be cleared.</p>")
                .setLogMessage("The ICMP outage identified on interface "+ipaddr + " has been cleared. Service is restored.")
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
