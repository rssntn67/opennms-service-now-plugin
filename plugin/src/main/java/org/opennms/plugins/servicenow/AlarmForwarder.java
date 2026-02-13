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
    private boolean start = true;

    private final EdgeService edgeService;

    public AlarmForwarder(ConnectionManager connectionManager,
                          ApiClientProvider apiClientProvider,
                          String filter,
                          EdgeService edgeservice) {
        this.connectionManager = Objects.requireNonNull(connectionManager);
        this.apiClientProvider = Objects.requireNonNull(apiClientProvider);
        this.filter = Objects.requireNonNull(filter);
        this.edgeService = Objects.requireNonNull(edgeservice);
    }

    @Override
    public void handleNewOrUpdatedAlarm(Alarm alarm) {
        sendAlarm(alarm);
    }

    private void sendAlarm(Alarm alarm) {
        // Map the alarm to the corresponding model object that the API requires
        if (!alarm.getReductionKey().startsWith(ALARM_UEI_NODE_DOWN) &&
            !alarm.getReductionKey().startsWith(ALARM_UEI_INTERFACE_DOWN) &&
            !(alarm.getReductionKey().startsWith(ALARM_UEI_SERVICE_DOWN) && alarm.getReductionKey().endsWith("ICMP"))
            )
        {
            LOG.debug("sendAlarm: not matching uei, skipping alarm with reduction key: {}", alarm.getReductionKey());
            return;
        }
        LOG.debug("sendAlarm: categories {}", alarm.getNode().getCategories());
        if (!alarm.getNode().getCategories().contains(filter)) {
            LOG.debug("sendAlarm: not matching filter {}, skipping alarm with reduction key: {}", filter, alarm.getReductionKey());
            return;
        }

        LOG.info("sendAlarm: processing alarm with reduction key: {}", alarm.getReductionKey());
        Alert alert = toAlert(alarm, edgeService.getParent(alarm.getNode()));
        LOG.info("sendAlarm: converted to {}", alert );

        try {
            apiClientProvider.send(
                    alert,
                    ClientManager.asApiClientCredentials(connectionManager.getConnection().orElseThrow()));
            LOG.info("sendAlarm: forwarded: id={} asset={}, node={}, parent={}", alert.getId(), alert.getAsset(), alert.getNode(), alert.getParentalNodeLabel());
        } catch (ApiException e) {
            LOG.error("sendAlarm: no forward: alarm {}, message: {}, body: {}",
                    alarm.getReductionKey(),
                    e.getMessage(),
                    e.getResponseBody(), e);
        }
    }

    @Override
    public void handleAlarmSnapshot(List<Alarm> alarms) {
        LOG.info("handleAlarmSnapshot: got {} alarms", alarms.size());
        if (!start)
            return;
        alarms.forEach(this::sendAlarm);
        start=false;
    }

    @Override
    public void handleDeletedAlarm(int alarmId, String reductionKey) {
        LOG.debug("handleDeletedAlarm: alarm:{} with reductionKey:{}", alarmId, reductionKey);
    }

    public static Alert toAlert(Alarm alarm, String parentNodeLabel) {
        Alert alert = new Alert();
        alert.setId(""+alarm.getId());
        alert.setTime(alarm.getLastEventTime());
        alert.setSource(alarm.getNode().getLocation());
        alert.setType("opennms network alarm");
        alert.setSeverity(toSeverity(alarm));
        alert.setMaintenance(false);
        alert.setDescription(alarm.getDescription().replaceAll("<p>","").replaceAll("</p>", "\n"));
        alert.setMetricName(alarm.getReductionKey());
        alert.setKey(alarm.getLogMessage());
        alert.setResource(alarm.getNode().getAssetRecord().getDescription());
        alert.setNode(alarm.getNode().getId().toString());
        alert.setAsset(alarm.getNode().getLabel());
        alert.setAlertTags(alarm.getNode().getCategories().toString());
        alert.setStatus(toStatus(alarm));
        alert.setParentalNodeLabel(parentNodeLabel);

        return alert;
    }

    private static Alert.Severity toSeverity(Alarm alarm) {
        return switch (alarm.getSeverity()) {
            case CLEARED -> Alert.Severity.CLEAR;
            case WARNING -> Alert.Severity.WARNING;
            case MINOR -> Alert.Severity.MINOR;
            case MAJOR -> Alert.Severity.MAJOR;
            case CRITICAL -> Alert.Severity.CRITICAL;
            default -> Alert.Severity.OK;
        };
    }


    private static Alert.Status toStatus(Alarm alarm) {
        return switch (alarm.getSeverity()) {
            case INDETERMINATE, CLEARED, NORMAL -> Alert.Status.UP;
            default -> Alert.Status.DOWN;
        };
    }

}
