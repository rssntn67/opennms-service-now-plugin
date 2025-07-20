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

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class AlarmForwarderTest {

    public static Alarm getAlarm() {
       return ImmutableAlarm.newBuilder()
                .setId(-10)
                .setReductionKey(AlarmForwarder.ALARM_UEI_NODE_DOWN+":-10")
                .setSeverity(Severity.CRITICAL)
                .setDescription("Description")
                .setLogMessage("LogMessage")
                .setNode(ImmutableNode.newBuilder()
                        .setId(-1)
                        .setLocation("Asia")
                        .setLabel("Node")
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

        assertThat(alert.getSeverity(), equalTo(Alert.Severity.CRITICAL));
        ObjectMapper mapper = new ObjectMapper();
        System.out.println(mapper.writeValueAsString(alert));
    }
}
