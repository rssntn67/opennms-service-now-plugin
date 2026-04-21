package org.opennms.plugins.servicenow;

import org.opennms.integration.api.v1.config.requisition.Requisition;
import org.opennms.integration.api.v1.config.requisition.RequisitionInterface;
import org.opennms.integration.api.v1.config.requisition.RequisitionNode;
import org.opennms.integration.api.v1.dao.NodeDao;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.integration.api.v1.requisition.RequisitionRepository;
import org.opennms.plugins.servicenow.model.AccessPoint;
import org.opennms.plugins.servicenow.model.InstallStatus;
import org.opennms.plugins.servicenow.model.NetworkDevice;
import org.opennms.plugins.servicenow.model.TipoApparato;
import org.opennms.plugins.servicenow.model.TipoCollegamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class AssetForwarder implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(AssetForwarder.class);

    private final String filter;
    private final String filterAccessPoint;
    private final String locationAccessPointSctt;
    private final String filterSwitch;
    private final String filterFirewall;
    private final String filterModemLte;
    private final String filterModemXdsl;

    private final NodeDao nodeDao;
    private final EdgeService edgeService;
    private final PluginEventForwarder eventForwarder;
    private final RequisitionRepository requisitionRepository;
    private final AssetSender assetSender;

    public AssetForwarder(String filter,
                          String filterAccessPoint,
                          String locationAccessPointSctt,
                          String filterSwitch,
                          String filterFirewall,
                          String filterModemLte,
                          String filterModemXdsl,
                          NodeDao nodeDao,
                          EdgeService edgeservice,
                          RequisitionRepository requisitionRepository,
                          PluginEventForwarder eventForwarder,
                          AssetSender assetSender) {
        this.filter = Objects.requireNonNull(filter);
        this.filterAccessPoint = Objects.requireNonNull(filterAccessPoint);
        this.locationAccessPointSctt = Objects.requireNonNull(locationAccessPointSctt);
        this.filterSwitch = Objects.requireNonNull(filterSwitch);
        this.filterFirewall = Objects.requireNonNull(filterFirewall);
        this.filterModemLte = Objects.requireNonNull(filterModemLte);
        this.filterModemXdsl = Objects.requireNonNull(filterModemXdsl);
        this.nodeDao = Objects.requireNonNull(nodeDao);
        this.edgeService = Objects.requireNonNull(edgeservice);
        this.eventForwarder = Objects.requireNonNull(eventForwarder);
        this.requisitionRepository = Objects.requireNonNull(requisitionRepository);
        this.assetSender = Objects.requireNonNull(assetSender);

        LOG.info("init: filter: {}", this.filter);
        LOG.info("init: filterAccessPoint: {}, filterSwitch: {}, filterFirewall: {}, filterModemLte: {}, filterModemXdsl: {}",
                this.filterAccessPoint, this.filterSwitch, this.filterFirewall, this.filterModemLte, this.filterModemXdsl);
    }

    public static String getAssetTag(Node node) {
        return getAssetTag(node.getForeignSource(), node.getForeignId());
    }

    public static String getAssetTag(String fs, String fid) {
        return fs + "::" + fid;
    }

    public void clearCache() {
        assetSender.clearCache();
    }

    public Map<String, String> getNetworkDeviceCache() {
        return assetSender.getNetworkDeviceCache();
    }

    public Map<String, String> getAccessPointCache() {
        return assetSender.getAccessPointCache();
    }

    public NetworkDevice toNetworkDevice(String json) {
        return assetSender.toNetworkDevice(json);
    }

    public AccessPoint toAccessPoint(String json) {
        return assetSender.toAccessPoint(json);
    }

    public boolean disableAsset(String assetTag) {
        return assetSender.disableAsset(assetTag);
    }

    public void sendAsset(Node node) {
        LOG.info("sendAsset: processing node with id:{} fs:{}, fid:{}",
                node.getId(),
                node.getForeignSource(),
                node.getForeignId());
        Requisition req = requisitionRepository.getDeployedRequisition(node.getForeignSource());
        if (req == null) {
            LOG.error("sendAsset: no requisition  for nodeId: {}, fs: {}", node.getId(), node.getForeignSource());
            eventForwarder.sendAssetFailed(node.getId(), "No requisition found");
            return;
        }
        RequisitionNode rn = null;
        for (RequisitionNode currRn : req.getNodes()) {
            if (currRn.getForeignId().equals(node.getForeignId())) {
                rn = currRn;
                break;
            }
        }
        if (rn == null) {
            LOG.error("sendAsset: no requisition node for nodeId: {}", node.getId());
            eventForwarder.sendAssetFailed(node.getId(), "No requisition node found");
            return;
        }
        if (rn.getInterfaces().isEmpty()) {
            LOG.error("sendAsset: no requisition interface for nodeId: {}", node.getId());
            eventForwarder.sendAssetFailed(node.getId(), "No ip address found");
            return;
        }
        RequisitionInterface ri = rn.getInterfaces().get(0);
        String ipaddress = ri.getIpAddress().getHostAddress();

        if (node.getCategories().contains(filterAccessPoint)) {
            AccessPoint ap = toAccessPoint(node, edgeService.getParent(node), ipaddress);
            if (assetSender.isUnchanged(ap.getAssetTag(), ap.hashCode())) {
                LOG.info("sendAsset: AccessPoint skipping unchanged asset: {}", ap.getAssetTag());
                return;
            }
            sendAccessPoint(node, ap);
            return;
        }
        if (node.getCategories().contains(filterSwitch)) {
            NetworkDevice nd = toNetworkDevice(node, edgeService.getParent(node), ipaddress, TipoApparato.SWITCH);
            if (assetSender.isUnchanged(nd.getAssetTag(), nd.hashCode())) {
                LOG.info("sendAsset: NetworkDevice Switch skipping unchanged asset: {}", nd.getAssetTag());
                return;
            }
            sendNetworkDevice(node, nd);
            return;
        }
        if (node.getCategories().contains(filterFirewall)) {
            NetworkDevice nd = toNetworkDevice(node, edgeService.getParent(node), ipaddress, TipoApparato.FIREWALL);
            if (assetSender.isUnchanged(nd.getAssetTag(), nd.hashCode())) {
                LOG.info("sendAsset: NetworkDevice Firewall skipping unchanged asset: {}", nd.getAssetTag());
                return;
            }
            sendNetworkDevice(node, nd);
            return;
        }
        if (node.getCategories().contains(filterModemLte)) {
            NetworkDevice nd = toNetworkDevice(node, edgeService.getParent(node), ipaddress, TipoApparato.MODEM_LTE);
            if (assetSender.isUnchanged(nd.getAssetTag(), nd.hashCode())) {
                LOG.info("sendAsset: NetworkDevice Modem LTE skipping unchanged asset: {}", nd.getAssetTag());
                return;
            }
            sendNetworkDevice(node, nd);
            return;
        }
        if (node.getCategories().contains(filterModemXdsl)) {
            NetworkDevice nd = toNetworkDevice(node, edgeService.getParent(node), ipaddress, TipoApparato.MODEM_XDSL);
            if (assetSender.isUnchanged(nd.getAssetTag(), nd.hashCode())) {
                LOG.info("sendAsset: NetworkDevice Model XDSL skipping unchanged asset: {}", nd.getAssetTag());
                return;
            }
            sendNetworkDevice(node, nd);
            return;
        }
        LOG.error("sendAsset: no match category for node {}", node.getId());
        eventForwarder.sendAssetFailed(node.getId(), "No matching category found");
    }

    public void sendAccessPoint(Node node, AccessPoint accessPoint) {
        assetSender.enqueue(node, accessPoint);
    }

    public void sendNetworkDevice(Node node, NetworkDevice networkDevice) {
        assetSender.enqueue(node, networkDevice);
    }

    public static String getLocation(Node node) {
        if (node.getAssetRecord().getDescription() == null
                || node.getAssetRecord().getDescription().isBlank()
                || node.getAssetRecord().getDescription().isEmpty()) {
            return node.getAssetRecord().getGeolocation().getAddress1() + ", " + node.getAssetRecord().getGeolocation().getCity();
        }
        return node.getAssetRecord().getDescription();
    }

    public NetworkDevice toNetworkDevice(Node node, String parentNodeLabel, String ipaddress, TipoApparato tipoApparato) {
        NetworkDevice networkDevice = new NetworkDevice();
        networkDevice.setSysClassName("u_cmdb_ci_apparati_di_rete");
        networkDevice.setCategoria("Reti Telecomunicazioni");
        networkDevice.setName(node.getLabel());
        networkDevice.setAssetTag(getAssetTag(node));
        networkDevice.setIpAddress(ipaddress);
        networkDevice.setParentalNode(parentNodeLabel);
        networkDevice.setModello(node.getAssetRecord().getOperatingSystem() + ":" + node.getAssetRecord().getModelNumber());
        networkDevice.setMarca(node.getAssetRecord().getVendor());
        networkDevice.setModelId("Apparati di Rete");
        networkDevice.setInstallStatus(InstallStatus.ATTIVO);
        networkDevice.setLocation(getLocation(node));
        networkDevice.setLatitudine(String.valueOf(node.getAssetRecord().getGeolocation().getLatitude()));
        networkDevice.setLongitudine(String.valueOf(node.getAssetRecord().getGeolocation().getLongitude()));
        networkDevice.setTipoApparato(tipoApparato);
        return networkDevice;
    }

    public AccessPoint toAccessPoint(Node node, String parentNodeLabel, String ipaddress) {
        AccessPoint accessPoint = new AccessPoint();
        accessPoint.setSysClassName("u_cmdb_ci_access_point");
        accessPoint.setCategoria("Wifi");
        accessPoint.setName(node.getLabel());
        accessPoint.setAssetTag(getAssetTag(node));
        accessPoint.setIpAddress(ipaddress);
        accessPoint.setParentalNode(parentNodeLabel);
        accessPoint.setModello(node.getAssetRecord().getOperatingSystem() + ":" + node.getAssetRecord().getModelNumber());
        accessPoint.setMarca(node.getAssetRecord().getVendor());
        accessPoint.setModelId("Access Point");
        accessPoint.setInstallStatus(InstallStatus.ATTIVO);
        accessPoint.setLocation(getLocation(node));
        accessPoint.setLatitudine(String.valueOf(node.getAssetRecord().getGeolocation().getLatitude()));
        accessPoint.setLongitudine(String.valueOf(node.getAssetRecord().getGeolocation().getLongitude()));
        accessPoint.setTipoCollegamento(getTipoCollegamento(node.getLocation()));
        accessPoint.setSerialNumber(node.getAssetRecord().getAssetNumber());
        return accessPoint;
    }

    private TipoCollegamento getTipoCollegamento(String location) {
        if (location.equals("Default")) {
            return TipoCollegamento.CAMPUS;
        }
        if (location.equals(locationAccessPointSctt)) {
            return TipoCollegamento.SCTT;
        }
        return TipoCollegamento.ALTRO;
    }

    @Override
    public void run() {
        LOG.info("run: calling");
        List<Node> nodes = nodeDao.getNodes().stream().filter(n -> n.getCategories().contains(filter)).toList();
        LOG.info("run: found: {} nodes", nodes.size());
        Set<String> currentAssetTags = nodes.stream().map(AssetForwarder::getAssetTag).collect(Collectors.toSet());
        currentAssetTags.forEach(h -> LOG.debug("run: found: {} hash", h));
        Set<String> cachedDeletedAssetTags = assetSender.getCachedAssetTags().stream()
                .filter(k -> !currentAssetTags.contains(k))
                .collect(Collectors.toSet());
        cachedDeletedAssetTags.forEach(assetSender::disableAsset);
        nodes.forEach(this::sendAsset);
    }
}