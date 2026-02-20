package org.opennms.plugins.servicenow;

import org.opennms.integration.api.v1.events.EventForwarder;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.integration.api.v1.model.immutables.ImmutableEventParameter;
import org.opennms.integration.api.v1.model.immutables.ImmutableInMemoryEvent;
import org.opennms.plugins.servicenow.client.ApiClientProvider;
import org.opennms.plugins.servicenow.client.ApiException;
import org.opennms.plugins.servicenow.client.ClientManager;
import org.opennms.plugins.servicenow.connection.ConnectionManager;
import org.opennms.plugins.servicenow.model.NetworkDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class AssetForwarder implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(AssetForwarder.class);

    private final ConnectionManager connectionManager;
    private final ApiClientProvider apiClientProvider;
    private final String filter;
    private boolean start = true;

    private final EdgeService edgeService;
    private final EventForwarder eventForwarder;

    private static final String UEI_PREFIX = "uei.opennms.org/opennms-service-nowPlugin";
    private static final String SEND_ASSET_FAILED_UEI = UEI_PREFIX + "/sendAssetFailed";
    private static final String SEND_ASSET_SUCCESSFUL_UEI = UEI_PREFIX + "/sendAssetSuccessful";

    public AssetForwarder(ConnectionManager connectionManager,
                          ApiClientProvider apiClientProvider,
                          String filter,
                          EdgeService edgeservice,
                          EventForwarder eventForwarder) {
        this.connectionManager = Objects.requireNonNull(connectionManager);
        this.apiClientProvider = Objects.requireNonNull(apiClientProvider);
        this.filter = Objects.requireNonNull(filter);
        this.edgeService = Objects.requireNonNull(edgeservice);
        this.eventForwarder = Objects.requireNonNull(eventForwarder);
        LOG.info("init: filter: {}", this.filter);
    }

    private void sendAsset(Node node) {
        // Map the alarm to the corresponding model object that the API requires
        if (!node.getCategories().contains(filter)) {
            LOG.debug("sendAsset: not matching filter {}, skipping asset: {}", filter, node.getId());
            return;
        }

        LOG.info("sendAsset: processing node: {}", node.getId());
        NetworkDevice networkDevice = toNetworkDevice(node, edgeService.getParent(node));
        LOG.info("sendAsset: converted to {}", networkDevice );

        try {
            apiClientProvider.send(
                    networkDevice,
                    ClientManager.asApiClientCredentials(connectionManager.getConnection().orElseThrow()));
            eventForwarder.sendAsync(ImmutableInMemoryEvent.newBuilder()
                    .setUei(SEND_ASSET_SUCCESSFUL_UEI)
                    .setNodeId(node.getId())
                    .build());
            LOG.info("sendAsset: forwarded: {}",  networkDevice);
        } catch (ApiException e) {
            eventForwarder.sendAsync(ImmutableInMemoryEvent.newBuilder()
                    .setUei(SEND_ASSET_FAILED_UEI)
                    .setNodeId(node.getId())
                    .addParameter(ImmutableEventParameter.newBuilder()
                            .setName("message")
                            .setValue(e.getMessage())
                            .build())
                    .build());
            LOG.error("sendAsset: failed to send:  {}, message: {}, body: {}",
                    node,
                    e.getMessage(),
                    e.getResponseBody(), e);
        }
    }


    public static NetworkDevice toNetworkDevice(Node node, String parentNodeLabel) {
        return null;
    }

    public static NetworkDevice toAccessPoint(Node node, String parentNodeLabel) {
        return null;
    }

    @Override
    public void run() {
        edgeService.getNodes().stream().filter(n  -> n.getCategories().contains(filter)).forEach(this::sendAsset);
    }

}
