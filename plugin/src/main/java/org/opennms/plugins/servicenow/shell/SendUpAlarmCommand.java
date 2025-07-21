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

@Command(scope = "opennms-service-now", name = "send-up-alarm", description = "Send Test Alarm Up.")
@Service
public class SendUpAlarmCommand implements Action {

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
                .setSeverity(Severity.CLEARED)
                .setDescription("Description Test Node Up ")
                .setLogMessage("Node Up Test")
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
