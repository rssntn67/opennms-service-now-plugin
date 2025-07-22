package org.opennms.plugins.servicenow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.integration.api.v1.model.Severity;
import org.opennms.integration.api.v1.model.immutables.ImmutableAlarm;
import org.opennms.integration.api.v1.model.immutables.ImmutableNode;
import org.opennms.integration.api.v1.model.immutables.ImmutableNodeAssetRecord;
import org.opennms.plugins.servicenow.model.Alert;

import java.util.Date;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class AlarmForwarderTest {

    private static final String nodeLabel = "Test-AlarmForwarderTest";
    private static final String description = "<p>Node " +
            nodeLabel +
            " which was previously down is" +
            " now up.</p> <p>This event is generated when node" +
            " outage processing determines that all interfaces on the node" +
            " are up.</p> <p>This event will cause any active" +
            " outages associated with this node to be cleared.</p>";

    private static final String logMsg = "Node "+nodeLabel+" is down.";

    private static final int alarmId = 123456;
    private static final int nodeId = 789;
    private final static Date now = new Date();

    public static Alarm getAlarm() {
       return ImmutableAlarm.newBuilder()
                .setId(alarmId)
                .setReductionKey(AlarmForwarder.ALARM_UEI_NODE_DOWN+"::"+nodeId)
                .setFirstEventTime(now)
                .setLastEventTime(now)
                .setSeverity(Severity.MAJOR)
                .setDescription(description)
                .setLogMessage(logMsg)
                .setNode(ImmutableNode.newBuilder()
                        .setId(nodeId)
                        .setLocation("Asia")
                        .setLabel(nodeLabel)
                        .setCategories(List.of("CategoryA", "CategoryB"))
                        .setAssetRecord(ImmutableNodeAssetRecord.newBuilder()
                                .setDescription("AssetRecordDescription")
                                .build())
                        .build()
                ).build();

    }
    @Test
    public void canConvertAlarmToAlert() throws JsonProcessingException {

        Alert alert = AlarmForwarder.toAlert(getAlarm());
        ObjectMapper mapper = new ObjectMapper();
        System.out.println(mapper.writeValueAsString(alert));

        assertThat(alert.getId(), equalTo(""+alarmId));
        assertThat(alert.getNode(), equalTo(nodeLabel));
        assertThat(alert.getAsset(), equalTo(""+nodeId));
        assertThat(alert.getMetricName(), equalTo(AlarmForwarder.ALARM_UEI_NODE_DOWN+"::"+nodeId));
        assertThat(alert.getKey(), equalTo(logMsg));
        assertThat(alert.getDescription(), equalTo(description.replaceAll("<p>","").replaceAll("</p>","\n")));
        assertThat(alert.getSeverity(), equalTo(Alert.Severity.MAJOR));
        assertThat(alert.getStatus(), equalTo(Alert.Status.DOWN));
        assertThat(alert.getAlertTags(), equalTo(List.of("CategoryA", "CategoryB").toString()));
        assertThat(alert.getSource(), equalTo("Asia"));
        assertThat(alert.getResource(), equalTo("AssetRecordDescription"));
        assertThat(alert.getType(), equalTo("opennms network alarm"));
        assertThat(alert.getTime(), equalTo(now));


    }
}
