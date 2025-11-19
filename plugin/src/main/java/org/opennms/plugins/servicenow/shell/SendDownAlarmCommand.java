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

@Command(scope = "opennms-service-now", name = "send-down-alarm", description = "Send Test Alarm Down.")
@Service
public class SendDownAlarmCommand implements Action {

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


    @Override
    public Object execute() {
        forwarder.handleNewOrUpdatedAlarm(getAlarm(this.alarmId, this.nodeId, this.nodeLabel, this.parentLabel));
        return null;
    }

    public static Alarm getAlarm(int alarmId, int nodeId, String nodeLabel, String parentLabel) {
        return ImmutableAlarm.newBuilder()
                .setId(alarmId)
                .setReductionKey(AlarmForwarder.ALARM_UEI_NODE_DOWN+":"+nodeId)
                .setSeverity(Severity.MAJOR)
                .setDescription("<p>All interfaces on node " +
                        nodeLabel +
                        " are" +
                        " down because of the following condition: generated test by service now plugin</p> <p>" +
                        " This event is generated when node outage processing determines" +
                        " that all interfaces on the node are down.</p> <p>" +
                        " New outage records have been created and service level" +
                        " availability calculations will be impacted until this outage" +
                        " is resolved.</p>")
                .setLogMessage("Node "+nodeLabel+" is down.")
                .setFirstEventTime(new Date())
                .setLastEventTime(new Date())
                .setNode(ImmutableNode.newBuilder()
                        .setId(nodeId)
                        .setLocation("Asia")
                        .setLabel(nodeLabel)
                        .setCategories(List.of("CategoryA", "CategoryB", "Minnovo","MinnovoTest"))
                        .addMetaData(ImmutableMetaData.newBuilder().setContext("provision")
                                .setKey("parent")
                                .setValue(parentLabel).build())
                        .setAssetRecord(ImmutableNodeAssetRecord.newBuilder()
                                .setDescription("AssetRecord.Description")
                                .build())
                        .build()
                ).build();

    }

}
