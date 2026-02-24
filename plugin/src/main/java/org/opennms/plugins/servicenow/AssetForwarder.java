package org.opennms.plugins.servicenow;

import org.opennms.integration.api.v1.dao.NodeDao;
import org.opennms.integration.api.v1.events.EventForwarder;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.integration.api.v1.model.immutables.ImmutableEventParameter;
import org.opennms.integration.api.v1.model.immutables.ImmutableInMemoryEvent;
import org.opennms.integration.api.v1.requisition.RequisitionRepository;
import org.opennms.plugins.servicenow.client.ApiClientProvider;
import org.opennms.plugins.servicenow.client.ApiException;
import org.opennms.plugins.servicenow.client.ClientManager;
import org.opennms.plugins.servicenow.connection.ConnectionManager;
import org.opennms.plugins.servicenow.model.AccessPoint;
import org.opennms.plugins.servicenow.model.InstallStatus;
import org.opennms.plugins.servicenow.model.NetworkDevice;
import org.opennms.plugins.servicenow.model.TipoApparato;
import org.opennms.plugins.servicenow.model.TipoCollegamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class AssetForwarder implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(AssetForwarder.class);

    private final ConnectionManager connectionManager;
    private final ApiClientProvider apiClientProvider;
    private final String filter;
    private final String filterAccessPoint;
    private final String filterSwitch;
    private final String filterFirewall;
    private final String filterModemLte;
    private final String filterModemXdsl;

    private final NodeDao nodeDao;
    private final EdgeService edgeService;
    private final EventForwarder eventForwarder;
    private final RequisitionRepository requisitionRepository;
    private final String hashCacheFile;
    private final String networkDeviceCacheFile;
    private final String accessPointCacheFile;
    private final Map<String, String> hashCache = new HashMap<>();
    private final Map<String, String> accessPointMap = new HashMap<>();
    private final Map<String, String> networkDeviceMap = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, String> fsFiIpAddressMap = new HashMap<>();
    private static final String UEI_PREFIX = "uei.opennms.org/opennms-service-nowPlugin";
    private static final String SEND_ASSET_FAILED_UEI = UEI_PREFIX + "/sendAssetFailed";
    private static final String SEND_ASSET_SUCCESSFUL_UEI = UEI_PREFIX + "/sendAssetSuccessful";

    public AssetForwarder(ConnectionManager connectionManager,
                          ApiClientProvider apiClientProvider,
                          String filter,
                          String filterAccessPoint,
                          String filterSwitch,
                          String filterFirewall,
                          String filterModemLte,
                          String filterModemXdsl,
                          NodeDao nodeDao,
                          EdgeService edgeservice,
                          RequisitionRepository requisitionRepository,
                          EventForwarder eventForwarder,
                          String assetCacheFilePrefix) {
        this.connectionManager = Objects.requireNonNull(connectionManager);
        this.apiClientProvider = Objects.requireNonNull(apiClientProvider);
        this.filter= Objects.requireNonNull(filter);
        this.filterAccessPoint = Objects.requireNonNull(filterAccessPoint);
        this.filterSwitch = Objects.requireNonNull(filterSwitch);
        this.filterFirewall = Objects.requireNonNull(filterFirewall);
        this.filterModemLte = Objects.requireNonNull(filterModemLte);
        this.filterModemXdsl = Objects.requireNonNull(filterModemXdsl);
        this.nodeDao = Objects.requireNonNull(nodeDao);
        this.edgeService = Objects.requireNonNull(edgeservice);
        this.eventForwarder = Objects.requireNonNull(eventForwarder);
        this.requisitionRepository = Objects.requireNonNull(requisitionRepository);
        this.hashCacheFile = assetCacheFilePrefix+".properties";
        this.networkDeviceCacheFile = assetCacheFilePrefix+"-NetworkDevice.properties";
        this.accessPointCacheFile = assetCacheFilePrefix+"-AccessPoint.properties";

        LOG.info("init: filterAccessPoint: {}, filterSwitch: {}, filterFirewall: {}, filterModemLte: {}, filterModemXdsl: {}",
                this.filterAccessPoint, this.filterSwitch, this.filterFirewall, this.filterModemLte, this.filterModemXdsl);

        loadCache();
        loadNetworkDeviceCache();
        loadAccessPointCache();
    }

    private static String getAssetTag(Node node) {
        return getAssetTag(node.getForeignSource(),node.getForeignId());
    }

    private static String getAssetTag(String fs, String fid) {
        return fs+"::"+fid;
    }

    private void loadCache() {
        Path path = Paths.get(hashCacheFile);
        if (!Files.exists(path)) {
            LOG.info("loadCache: hash cache file not found, starting fresh: {}", hashCacheFile);
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            props.forEach((k, v) -> hashCache.put((String) k,  (String) v));
            LOG.info("loadCache: loaded {} entries from {}", hashCache.size(), hashCacheFile);
        } catch (IOException e) {
            LOG.warn("loadCache: failed to read cache file {}, starting fresh", hashCacheFile, e);
        }
    }

    private void loadNetworkDeviceCache() {
        Path path = Paths.get(networkDeviceCacheFile);
        if (!Files.exists(path)) {
            LOG.info("loadNetworkDeviceCache: cache file not found, starting fresh: {}", networkDeviceCacheFile);
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            props.forEach((k, v) -> networkDeviceMap.put((String) k,  (String) v));
            LOG.info("loadNetworkDeviceCache: loaded {} entries from {}", networkDeviceMap.size(), networkDeviceCacheFile);
        } catch (IOException e) {
            LOG.warn("loadNetworkDeviceCache: failed to read cache file {}, starting fresh", networkDeviceCacheFile, e);
        }
    }

    private void loadAccessPointCache() {
        Path path = Paths.get(accessPointCacheFile);
        if (!Files.exists(path)) {
            LOG.info("loadAccessPointCache: cache file not found, starting fresh: {}", accessPointCacheFile);
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            props.forEach((k, v) -> accessPointMap.put((String) k,  (String) v));
            LOG.info("accessPointCachePath: loaded {} entries from {}", accessPointMap.size(), accessPointCacheFile);
        } catch (IOException e) {
            LOG.warn("accessPointCachePath: failed to read cache file {}, starting fresh", accessPointCacheFile, e);
        }
    }

    private boolean isUnchanged(String assetTag, int hash) {
            return String.valueOf(hash).equals(hashCache.get(assetTag));
    }

    private void updateCache(String assetTag, int hash) {
        hashCache.put(assetTag, String.valueOf(hash));
        Properties props = new Properties();
        props.putAll(hashCache);
        try (OutputStream out = Files.newOutputStream(Paths.get(hashCacheFile))) {
            props.store(out, null);
        } catch (IOException e) {
            LOG.error("updateCache: failed to write cache file {}", hashCacheFile, e);
        }
    }

    private void updateDataCache(NetworkDevice networkDevice) {
        networkDeviceMap.put(networkDevice.getAssetTag(), toJson(networkDevice));
        Properties props = new Properties();
        props.putAll(networkDeviceMap);
        try (OutputStream out = Files.newOutputStream(Paths.get(networkDeviceCacheFile))) {
            props.store(out, null);
        } catch (IOException e) {
            LOG.error("updateDataCache: failed to write data cache file {}", networkDeviceCacheFile, e);
        }
    }

    private void updateDataCache(AccessPoint accessPoint) {
        accessPointMap.put(accessPoint.getAssetTag(), toJson(accessPoint));
        Properties props = new Properties();
        props.putAll(accessPointMap);
        try (OutputStream out = Files.newOutputStream(Paths.get(accessPointCacheFile))) {
            props.store(out, null);
        } catch (IOException e) {
            LOG.error("updateDataCache: failed to write data cache file {}", accessPointCacheFile, e);
        }
    }

    private NetworkDevice toNetworkDevice(String json) {
        try {
            return objectMapper.readValue(json, NetworkDevice.class);
        } catch (JsonProcessingException e) {
            LOG.error("fromJson: failed to unserialize {}", json, e);
        }
        return null;
    }

    private AccessPoint toAccessPoint(String json) {
        try {
            return objectMapper.readValue(json, AccessPoint.class);
        } catch (JsonProcessingException e) {
            LOG.error("fromJson: failed to unserialize {}", json, e);
        }
        return null;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            LOG.error("toJson: failed to serialize {}", obj, e);
            return "";
        }
    }

    public void sendAsset(Node node) {
        // Map the alarm to the corresponding model object that the API requires
        LOG.info("sendAsset: processing node with id:{} fs:{}, fid:{}",
                node.getId(),
                node.getForeignSource(),
                node.getForeignId());
        if (!fsFiIpAddressMap.containsKey(getAssetTag(node))) {
            LOG.error("sendAsset: no ip address for node {}", node.getId());
            eventForwarder.sendAsync(ImmutableInMemoryEvent.newBuilder()
                    .setUei(SEND_ASSET_FAILED_UEI)
                    .setNodeId(node.getId())
                    .addParameter(ImmutableEventParameter.newBuilder()
                            .setName("message")
                            .setValue("No ip address found")
                            .build())
                    .build());
            return;
        }
        String ipaddress = fsFiIpAddressMap.get(getAssetTag(node));
        if (node.getCategories().contains(filterAccessPoint)) {
            sendAccessPoint(node, toAccessPoint(node, edgeService.getParent(node), ipaddress));
            return;
        }
        if (node.getCategories().contains(filterSwitch)) {
            sendNetworkDevice(node, toNetworkDevice(node, edgeService.getParent(node), ipaddress, TipoApparato.SWITCH));
            return;
        }
        if (node.getCategories().contains(filterFirewall)) {
            sendNetworkDevice(node, toNetworkDevice(node, edgeService.getParent(node), ipaddress, TipoApparato.FIREWALL));
            return;
        }
        if (node.getCategories().contains(filterModemLte)) {
            sendNetworkDevice(node, toNetworkDevice(node, edgeService.getParent(node), ipaddress, TipoApparato.MODEM_LTE));
            return;
        }
        if (node.getCategories().contains(filterModemXdsl)) {
            sendNetworkDevice(node, toNetworkDevice(node, edgeService.getParent(node), ipaddress, TipoApparato.MODEM_XDSL));
            return;
        }
        LOG.error("sendAsset: no match category for node {}", node.getId());
        eventForwarder.sendAsync(ImmutableInMemoryEvent.newBuilder()
                .setUei(SEND_ASSET_FAILED_UEI)
                .setNodeId(node.getId())
                .addParameter(ImmutableEventParameter.newBuilder()
                        .setName("message")
                        .setValue("No matching category found")
                        .build())
                .build());
    }

    public void sendAccessPoint(Node node, AccessPoint accessPoint) {
        if (isUnchanged(accessPoint.getAssetTag(), accessPoint.hashCode())) {
            LOG.debug("sendAccessPoint: skipping unchanged asset: {}", accessPoint.getAssetTag());
            return;
        }
        LOG.info("sendAccessPoint: converted to {}", accessPoint);
        try {
            apiClientProvider.send(
                    accessPoint,
                    ClientManager.asApiClientCredentials(connectionManager.getConnection().orElseThrow()));
            updateCache(accessPoint.getAssetTag(), accessPoint.hashCode());
            updateDataCache(accessPoint);
            eventForwarder.sendAsync(ImmutableInMemoryEvent.newBuilder()
                    .setUei(SEND_ASSET_SUCCESSFUL_UEI)
                    .setNodeId(node.getId())
                    .build());
            LOG.info("sendAccessPoint: forwarded: {}",  accessPoint);
        } catch (ApiException e) {
            eventForwarder.sendAsync(ImmutableInMemoryEvent.newBuilder()
                    .setUei(SEND_ASSET_FAILED_UEI)
                    .setNodeId(node.getId())
                    .addParameter(ImmutableEventParameter.newBuilder()
                            .setName("message")
                            .setValue(e.getMessage())
                            .build())
                    .build());
            LOG.error("sendAccessPoint: failed to send:  {}, message: {}, body: {}",
                    node,
                    e.getMessage(),
                    e.getResponseBody(), e);
        }
    }

    public void sendNetworkDevice(Node node, NetworkDevice networkDevice) {
        if (isUnchanged(networkDevice.getAssetTag(), networkDevice.hashCode())) {
            LOG.debug("sendNetworkDevice: skipping unchanged asset: {}", networkDevice.getAssetTag());
            return;
        }
        LOG.info("sendNetworkDevice: converted to {}", networkDevice);
        try {
            apiClientProvider.send(
                    networkDevice,
                    ClientManager.asApiClientCredentials(connectionManager.getConnection().orElseThrow()));
            updateCache(networkDevice.getAssetTag(), networkDevice.hashCode());
            updateDataCache(networkDevice);
            eventForwarder.sendAsync(ImmutableInMemoryEvent.newBuilder()
                    .setUei(SEND_ASSET_SUCCESSFUL_UEI)
                    .setNodeId(node.getId())
                    .build());
            LOG.info("sendNetworkDevice: forwarded: {}",  networkDevice);
        } catch (ApiException e) {
            eventForwarder.sendAsync(ImmutableInMemoryEvent.newBuilder()
                    .setUei(SEND_ASSET_FAILED_UEI)
                    .setNodeId(node.getId())
                    .addParameter(ImmutableEventParameter.newBuilder()
                            .setName("message")
                            .setValue(e.getMessage())
                            .build())
                    .build());
            LOG.error("sendNetworkDevice: failed to send:  {}, message: {}, body: {}",
                    node,
                    e.getMessage(),
                    e.getResponseBody(), e);
        }
    }

    public static String getLocation(Node node) {
        if (node.getAssetRecord().getDescription() == null
                || node.getAssetRecord().getDescription().isBlank()
                || node.getAssetRecord().getDescription().isEmpty())  {
            return node.getAssetRecord().getGeolocation().getAddress1()+ ", " + node.getAssetRecord().getGeolocation().getCity();
        }
        return node.getAssetRecord().getDescription();
    }

    public static NetworkDevice toNetworkDevice(Node node, String parentNodeLabel, String ipaddress, TipoApparato tipoApparato) {
        NetworkDevice networkDevice = new NetworkDevice();
        networkDevice.setSysClassName("u_cmdb_ci_apparati_di_rete");
        networkDevice.setCategoria("Reti Telecomunicazioni");
        networkDevice.setName(node.getLabel());
        networkDevice.setAssetTag(getAssetTag(node));
        networkDevice.setIpAddress(ipaddress);
        networkDevice.setParentalNode(parentNodeLabel);
        networkDevice.setModello(node.getAssetRecord().getModelNumber());
        networkDevice.setMarca(node.getAssetRecord().getVendor());
        networkDevice.setModelId(node.getAssetRecord().getOperatingSystem());
        networkDevice.setInstallStatus(InstallStatus.ATTIVO);

        //location
        networkDevice.setLocation(getLocation(node));
        networkDevice.setLatitudine(String.valueOf(node.getAssetRecord().getGeolocation().getLatitude()));
        networkDevice.setLongitudine(String.valueOf(node.getAssetRecord().getGeolocation().getLongitude()));

        //specific network device
        networkDevice.setTipoApparato(tipoApparato);

        return networkDevice;
    }

    public static AccessPoint toAccessPoint(Node node, String parentNodeLabel, String ipaddress) {
        AccessPoint accessPoint = new AccessPoint();
        accessPoint.setSysClassName("u_cmdb_ci_access_point");
        accessPoint.setCategoria("Wifi");
        accessPoint.setName(node.getLabel());
        accessPoint.setAssetTag(getAssetTag(node));
        accessPoint.setIpAddress(ipaddress);
        accessPoint.setParentalNode(parentNodeLabel);
        accessPoint.setModello(node.getAssetRecord().getModelNumber());
        accessPoint.setMarca(node.getAssetRecord().getVendor());
        accessPoint.setModelId(node.getAssetRecord().getOperatingSystem());
        accessPoint.setInstallStatus(InstallStatus.ATTIVO);

        //location
        accessPoint.setLocation(getLocation(node));
        accessPoint.setLatitudine(String.valueOf(node.getAssetRecord().getGeolocation().getLatitude()));
        accessPoint.setLongitudine(String.valueOf(node.getAssetRecord().getGeolocation().getLongitude()));

        //specific access point
        accessPoint.setTipoCollegamento(getTipoCollegamento(node.getLocation()));
        accessPoint.setSerialNumber(node.getAssetRecord().getAssetNumber());
        return accessPoint;
    }

    private static TipoCollegamento getTipoCollegamento(String location) {
        return switch (location) {
            case "Default" -> TipoCollegamento.CAMPUS;
            case "sctt" -> TipoCollegamento.SCTT;
            default -> TipoCollegamento.ALTRO;
        };
    }

    private Set<String> pruneCache(Set<String> currentAssetTags) {
        return hashCache.keySet().stream()
                .filter(k -> !currentAssetTags.contains(k))
                .collect(Collectors.toSet());
    }

    @Override
    public void run() {
        List<Node> nodes = nodeDao.getNodes().stream().filter(n -> n.getCategories().contains(filter)).toList();
        Set<String> currentAssetTags = nodes.stream().map(AssetForwarder::getAssetTag).collect(Collectors.toSet());
        pruneCache(currentAssetTags).forEach(assetTag -> {
            if (networkDeviceMap.containsKey(assetTag)) {
                NetworkDevice nd = toNetworkDevice(networkDeviceMap.get(assetTag));
                if (nd == null) {
                    LOG.error("run: no data got NetworkElement: {}", assetTag);
                    return;
                }
                nd.setInstallStatus(InstallStatus.DISATTIVO);
                sendNetworkDevice(null, nd);
                return;
            }
            if (accessPointMap.containsKey(assetTag)) {
                AccessPoint ap = toAccessPoint(accessPointMap.get(assetTag));
                if (ap == null) {
                    LOG.error("run: no data got AcccessPoint: {}", assetTag);
                    return;
                }
                ap.setInstallStatus(InstallStatus.DISATTIVO);
                sendAccessPoint(null, ap);
            }
        });

        Set<String> foreignSources = nodes.stream()
                .map(Node::getForeignSource)
                .collect(Collectors.toSet());

        fsFiIpAddressMap.clear();
        foreignSources.forEach(fs -> {
            var requisition = requisitionRepository.getDeployedRequisition(fs);
            if (requisition == null) {
                LOG.warn("run: no deployed requisition for foreignSource: {}", fs);
                return;
            }
            requisition.getNodes().stream()
                    .filter(rn -> rn.getInterfaces() != null && !rn.getInterfaces().isEmpty())
                    .forEach(rn -> fsFiIpAddressMap.put(
                            getAssetTag(fs, rn.getForeignId()),
                            rn.getInterfaces().getFirst().getIpAddress().getHostAddress()
                    ));
        });

        LOG.debug("run: AssetTagIpAddressMap: {}", fsFiIpAddressMap);
        nodes.forEach(this::sendAsset);

        fsFiIpAddressMap.clear();
    }

}
