package org.opennms.plugins.servicenow;

import org.opennms.integration.api.v1.alarms.AlarmLifecycleListener;
import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.plugins.servicenow.client.ApiClientProvider;
import org.opennms.plugins.servicenow.client.ApiException;
import org.opennms.plugins.servicenow.client.ClientManager;
import org.opennms.plugins.servicenow.connection.ConnectionManager;
import org.opennms.plugins.servicenow.model.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

public class AlarmForwarder implements AlarmLifecycleListener {
    private static final Logger LOG = LoggerFactory.getLogger(AlarmForwarder.class);

    public static final String ALARM_UEI_NODE_DOWN = "uei.opennms.org/nodes/nodeDown";
    public static final String ALARM_UEI_INTERFACE_DOWN = "uei.opennms.org/nodes/interfaceDown";
    public static final String ALARM_UEI_SERVICE_DOWN = "uei.opennms.org/nodes/nodeLostService";

    private final ConnectionManager connectionManager;
    private final ApiClientProvider apiClientProvider;
    private final String filter;

    public AlarmForwarder(ConnectionManager connectionManager, ApiClientProvider apiClientProvider, String filter) {
        this.connectionManager = Objects.requireNonNull(connectionManager);
        this.apiClientProvider = Objects.requireNonNull(apiClientProvider);
        this.filter = Objects.requireNonNull(filter);
    }

    @Override
    public void handleNewOrUpdatedAlarm(Alarm alarm) {
        LOG.debug("handleNewOrUpdatedAlarm: parsing alarm with reduction key: {}", alarm.getReductionKey());
        // Map the alarm to the corresponding model object that the API requires
        if (!alarm.getReductionKey().startsWith(ALARM_UEI_NODE_DOWN) &&
            !alarm.getReductionKey().startsWith(ALARM_UEI_INTERFACE_DOWN) &&
            !(alarm.getReductionKey().startsWith(ALARM_UEI_SERVICE_DOWN) && alarm.getReductionKey().endsWith("ICMP"))
            )
        {
            LOG.debug("handleNewOrUpdatedAlarm: not matching uei, skipping alarm with reduction key: {}", alarm.getReductionKey());
            return;
        }
        LOG.debug("handleNewOrUpdatedAlarm: categories {}", alarm.getNode().getCategories());
        if (!alarm.getNode().getCategories().contains(filter)) {
            LOG.debug("handleNewOrUpdatedAlarm: not matching filter {}, skipping alarm with reduction key: {}", filter, alarm.getReductionKey());
            return;
        }

        try {
            Alert alert = toAlert(alarm);
            LOG.debug("handleNewOrUpdatedAlarm: converted to {}", alarm );
            apiClientProvider.send(alert, ClientManager.asApiClientCredentials(connectionManager.getConnection().orElseThrow()));
        } catch (ApiException e) {
            LOG.error("handleNewOrUpdatedAlarm: no forward: alarm {}, message: {}, body: {}",
                    alarm.getReductionKey(),
                    e.getMessage(),
                    e.getResponseBody(), e);
        }
    }

    @Override
    public void handleAlarmSnapshot(List<Alarm> alarms) {
        // pass
    }

    @Override
    public void handleDeletedAlarm(int alarmId, String reductionKey) {
        // pass
    }

    public static Alert toAlert(Alarm alarm) {
        Alert alert = new Alert();
        alert.setId(""+alarm.getId());
        alert.setTime(alarm.getLastEventTime());
        alert.setSource(alarm.getNode().getLocation());
        alert.setType("opennms network alarm");
        alert.setSeverity(toSeverity(alarm));
        alert.setMaintenance(false);
        alert.setDescription(alarm.getDescription());
        alert.setMetricName(alarm.getReductionKey());
        alert.setKey(alarm.getLogMessage());
        alert.setResource(alarm.getNode().getAssetRecord().getDescription());
        alert.setNode(alarm.getNode().getLabel());
        alert.setAsset(alarm.getNode().getId().toString());
        alert.setAlertTags(alarm.getNode().getCategories().toString());
        alert.setStatus(toStatus(alarm));
        return alert;
    }

    private static Alert.Severity toSeverity(Alarm alarm) {
        switch (alarm.getSeverity()) {
            case CLEARED:
                return Alert.Severity.CLEAR;
            case WARNING:
                return Alert.Severity.WARNING;
            case MINOR:
                return Alert.Severity.MINOR;
            case MAJOR:
                return Alert.Severity.MAJOR;
            case CRITICAL:
                return Alert.Severity.CRITICAL;
            default:
                return Alert.Severity.OK;
        }
    }


    private static Alert.Status toStatus(Alarm alarm) {
        switch (alarm.getSeverity()) {
            case INDETERMINATE:
            case CLEARED:
            case NORMAL:
                return Alert.Status.UP;
            case WARNING:
            case MINOR:
            case MAJOR:
            case CRITICAL:
            default:
                return Alert.Status.DOWN;
        }
    }

}
