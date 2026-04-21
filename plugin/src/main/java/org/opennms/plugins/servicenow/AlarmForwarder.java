package org.opennms.plugins.servicenow;

import org.opennms.integration.api.v1.alarms.AlarmLifecycleListener;
import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.plugins.servicenow.model.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class AlarmForwarder implements AlarmLifecycleListener {
    private static final Logger LOG = LoggerFactory.getLogger(AlarmForwarder.class);

    public static final String ALARM_UEI_NODE_DOWN = "uei.opennms.org/nodes/nodeDown";
    public static final String ALARM_UEI_INTERFACE_DOWN = "uei.opennms.org/nodes/interfaceDown";
    public static final String ALARM_UEI_SERVICE_DOWN = "uei.opennms.org/nodes/nodeLostService";

    private final String filter;
    private final AlarmSender alarmSender;
    private final AtomicBoolean starting = new AtomicBoolean(true);

    public AlarmForwarder(String filter, AlarmSender alarmSender) {
        this.filter = Objects.requireNonNull(filter);
        this.alarmSender = Objects.requireNonNull(alarmSender);
        LOG.info("init: filter: {}", this.filter);
    }

    @Override
    public void handleNewOrUpdatedAlarm(Alarm alarm) {
        if (isToForward(alarm))
            alarmSender.enqueue(alarm);
    }

    private boolean isToForward(Alarm alarm) {
        if (!alarm.getReductionKey().startsWith(ALARM_UEI_NODE_DOWN) &&
                !alarm.getReductionKey().startsWith(ALARM_UEI_INTERFACE_DOWN) &&
                !(alarm.getReductionKey().startsWith(ALARM_UEI_SERVICE_DOWN) && alarm.getReductionKey().endsWith("ICMP"))
        )
        {
            LOG.debug("isToForward: not matching uei, skipping alarm with reduction key: {}", alarm.getReductionKey());
            return false;
        }
        LOG.debug("isToForward: categories {}", alarm.getNode().getCategories());
        if (!alarm.getNode().getCategories().contains(filter)) {
            LOG.debug("isToForward: not matching filter {}, skipping alarm with reduction key: {}", filter, alarm.getReductionKey());
            return false;
        }
        return true;
    }

    @Override
    public void handleAlarmSnapshot(List<Alarm> alarms) {
        LOG.debug("handleAlarmSnapshot: got {} alarms", alarms.size());
        if (!starting.get())
            return;
        LOG.info("handleAlarmSnapshot: starting adding {} alarms to forward", alarms.size());
        alarms.stream().filter(this::isToForward).forEach(alarmSender::enqueue);
        starting.set(false);
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