package org.opennms.plugins.servicenow.shell;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.integration.api.v1.model.Severity;
import org.opennms.integration.api.v1.model.immutables.ImmutableAlarm;
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

    @Override
    public Object execute() {
        forwarder.handleNewOrUpdatedAlarm(getAlarm());
        return null;
    }

    public static Alarm getAlarm() {
        return ImmutableAlarm.newBuilder()
                .setId(-1000)
                .setReductionKey(AlarmForwarder.ALARM_UEI_NODE_DOWN+":-1")
                .setSeverity(Severity.CRITICAL)
                .setDescription("Description Test Node Down ")
                .setLogMessage("Node Down Test")
                .setFirstEventTime(new Date())
                .setLastEventTime(new Date())
                .setNode(ImmutableNode.newBuilder()
                        .setId(-1)
                        .setLocation("Asia")
                        .setLabel("Node")
                        .setCategories(List.of("CategoryA", "CategoryB", "Minnovo"))
                        .setAssetRecord(ImmutableNodeAssetRecord.newBuilder()
                                .setDescription("AssetRecordDescription")
                                .build())
                        .build()
                ).build();

    }

}
