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

@Command(scope = "opennms-service-now", name = "send-up-alarm", description = "Send Test Alarm Up.")
@Service
public class SendUpAlarmCommand implements Action {

    @Reference
    private AlarmForwarder forwarder;

    @Argument(name = "alarmId", description = "Alarm Id", required = true)
    public int alarmId = -1000;

    @Argument(index = 1, name = "nodeId", description = "nodeid of the asset", required = true)
    public int nodeId = -1;

    @Override
    public Object execute() {
        forwarder.handleNewOrUpdatedAlarm(getAlarm(this.alarmId, this.nodeId));
        return null;
    }

    public static Alarm getAlarm(int alarmId, int nodeId) {
        String nodeLabel = "Test-"+nodeId;
        return ImmutableAlarm.newBuilder()
                .setId(alarmId)
                .setReductionKey(AlarmForwarder.ALARM_UEI_NODE_DOWN+":"+nodeId)
                .setSeverity(Severity.CLEARED)
                .setDescription("<p>Node " +
                        nodeLabel +
                        " which was previously down is" +
                        " now up.</p> <p>This event is generated when node" +
                        " outage processing determines that all interfaces on the node" +
                        " are up.</p> <p>This event will cause any active" +
                        " outages associated with this node to be cleared.</p>")
                .setLogMessage("Node "+nodeLabel+" is up.")
                .setFirstEventTime(new Date())
                .setLastEventTime(new Date())
                .setNode(ImmutableNode.newBuilder()
                        .setId(nodeId)
                        .setLocation("Asia")
                        .setLabel(nodeLabel)
                        .setCategories(List.of("CategoryA", "CategoryB", "Minnovo", "MinnovoTest"))
                        .addMetaData(ImmutableMetaData.newBuilder().setContext("provision")
                                .setKey("parent")
                                .setValue("parentNodeLabel").build())
                        .setAssetRecord(ImmutableNodeAssetRecord.newBuilder()
                                .setDescription("AssetRecord.Description")
                                .build())
                        .build()
                ).build();

    }

}
