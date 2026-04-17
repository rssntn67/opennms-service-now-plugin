package org.opennms.plugins.servicenow;

import org.opennms.integration.api.v1.config.requisition.Requisition;
import org.opennms.integration.api.v1.config.requisition.RequisitionInterface;
import org.opennms.integration.api.v1.config.requisition.RequisitionNode;
import org.opennms.integration.api.v1.dao.NodeDao;
import org.opennms.integration.api.v1.model.Node;
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
    private final String locationAccessPointSctt;
    private final String filterSwitch;
    private final String filterFirewall;
    private final String filterModemLte;
    private final String filterModemXdsl;

    private final NodeDao nodeDao;
    private final EdgeService edgeService;
    private final PluginEventForwarder eventForwarder;
    private final RequisitionRepository requisitionRepository;
    private final String hashCacheFile;
    private final String networkDeviceCacheFile;
    private final String accessPointCacheFile;
    private final Map<String, String> hashCache = new HashMap<>();
    private final Map<String, String> accessPointMap = new HashMap<>();
    private final Map<String, String> networkDeviceMap = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AssetForwarder(ConnectionManager connectionManager,
                          ApiClientProvider apiClientProvider,
                          String filter,
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
                          String assetCacheFilePrefix) {
        this.connectionManager = Objects.requireNonNull(connectionManager);
        this.apiClientProvider = Objects.requireNonNull(apiClientProvider);
        this.filter= Objects.requireNonNull(filter);
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
        this.hashCacheFile = assetCacheFilePrefix+".properties";
        this.networkDeviceCacheFile = assetCacheFilePrefix+"-NetworkDevice.properties";
        this.accessPointCacheFile = assetCacheFilePrefix+"-AccessPoint.properties";

        LOG.info("init: filter: {}", this.filter);

        LOG.info("init: filterAccessPoint: {}, filterSwitch: {}, filterFirewall: {}, filterModemLte: {}, filterModemXdsl: {}",
                this.filterAccessPoint, this.filterSwitch, this.filterFirewall, this.filterModemLte, this.filterModemXdsl);

        loadCache();
        loadNetworkDeviceCache();
        loadAccessPointCache();
    }

    public static String getAssetTag(Node node) {
        return getAssetTag(node.getForeignSource(),node.getForeignId());
    }

    public static String getAssetTag(String fs, String fid) {
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

    public void clearCache() {
        hashCache.clear();
        networkDeviceMap.clear();
        accessPointMap.clear();
        for (String file : new String[]{hashCacheFile, networkDeviceCacheFile, accessPointCacheFile}) {
            try {
                Files.deleteIfExists(Paths.get(file));
                LOG.info("clearCache: deleted {}", file);
            } catch (IOException e) {
                LOG.error("clearCache: failed to delete {}", file, e);
            }
        }
    }

    public Map<String, String> getNetworkDeviceCache() {
        return networkDeviceMap;
    }

    public Map<String, String> getAccessPointCache() {
        return accessPointMap;
    }

    public NetworkDevice toNetworkDevice(String json) {
        try {
            return objectMapper.readValue(json, NetworkDevice.class);
        } catch (JsonProcessingException e) {
            LOG.error("fromJson: failed to unserialize {}", json, e);
        }
        return null;
    }

    public AccessPoint toAccessPoint(String json) {
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
        Requisition req = requisitionRepository.getDeployedRequisition(node.getForeignSource());
        if (req == null) {
            LOG.error("sendAsset: no requisition  for nodeId: {}, fs: {}", node.getId(), node.getForeignSource());
            eventForwarder.sendAssetFailed(node.getId(), "No requisition found");
            return;
        }
        RequisitionNode rn = null;
        for (RequisitionNode currRn: req.getNodes()) {
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
            if (isUnchanged(ap.getAssetTag(), ap.hashCode())) {
                LOG.info("sendAsset: AccessPoint skipping unchanged asset: {}", ap.getAssetTag());
                return;
            }
            sendAccessPoint(node, ap);
            return;
        }
        if (node.getCategories().contains(filterSwitch)) {
            NetworkDevice nd = toNetworkDevice(node, edgeService.getParent(node), ipaddress, TipoApparato.SWITCH);
            if (isUnchanged(nd.getAssetTag(), nd.hashCode())) {
                LOG.info("sendAsset: NetworkDevice Switch skipping unchanged asset: {}", nd.getAssetTag());
                return;
            }
            sendNetworkDevice(node, nd);
            return;
        }
        if (node.getCategories().contains(filterFirewall)) {
            NetworkDevice nd = toNetworkDevice(node, edgeService.getParent(node), ipaddress, TipoApparato.FIREWALL);
            if (isUnchanged(nd.getAssetTag(), nd.hashCode())) {
                LOG.info("sendAsset: NetworkDevice Firewall skipping unchanged asset: {}", nd.getAssetTag());
                return;
            }
            sendNetworkDevice(node, nd);
            return;
        }
        if (node.getCategories().contains(filterModemLte)) {
            NetworkDevice nd = toNetworkDevice(node, edgeService.getParent(node), ipaddress, TipoApparato.MODEM_LTE);
            if (isUnchanged(nd.getAssetTag(), nd.hashCode())) {
                LOG.info("sendAsset: NetworkDevice Modem LTE skipping unchanged asset: {}", nd.getAssetTag());
                return;
            }
            sendNetworkDevice(node, nd);
            return;
        }
        if (node.getCategories().contains(filterModemXdsl)) {
            NetworkDevice nd = toNetworkDevice(node, edgeService.getParent(node), ipaddress, TipoApparato.MODEM_XDSL);
            if (isUnchanged(nd.getAssetTag(), nd.hashCode())) {
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
        LOG.info("sendAccessPoint: converted to {}", accessPoint);
        try {
            apiClientProvider.send(
                    accessPoint,
                    ClientManager.asApiClientCredentials(connectionManager.getConnection().orElseThrow()));
            updateCache(accessPoint.getAssetTag(), accessPoint.hashCode());
            updateDataCache(accessPoint);
            if (node != null) {
                eventForwarder.sendAssetSuccessful(node.getId(), accessPoint.getAssetTag());
            } else {
                eventForwarder.sendAssetSuccessful(accessPoint.getAssetTag());
            }
            LOG.info("sendAccessPoint: forwarded: {}",  accessPoint);
        } catch (ApiException e) {
            LOG.error("sendAccessPoint: failed to send:  {}, message: {}, body: {}",
                    node,
                    e.getMessage(),
                    e.getResponseBody(), e);
            if (node != null) {
                eventForwarder.sendAssetFailed(node.getId(), e.getMessage(), accessPoint.getAssetTag());
            } else {
                eventForwarder.sendAssetFailed(e.getMessage(), accessPoint.getAssetTag());
            }
        }
    }

    public void sendNetworkDevice(Node node, NetworkDevice networkDevice) {
        LOG.info("sendNetworkDevice: converted to {}", networkDevice);
        try {
            apiClientProvider.send(
                    networkDevice,
                    ClientManager.asApiClientCredentials(connectionManager.getConnection().orElseThrow()));
            updateCache(networkDevice.getAssetTag(), networkDevice.hashCode());
            updateDataCache(networkDevice);
            if (node != null) {
                eventForwarder.sendAssetSuccessful(node.getId(), networkDevice.getAssetTag());
            } else {
                eventForwarder.sendAssetSuccessful(networkDevice.getAssetTag());
            }
            LOG.info("sendNetworkDevice: forwarded: {}",  networkDevice);
        } catch (ApiException e) {
            if (node != null) {
                eventForwarder.sendAssetFailed(node.getId(), e.getMessage(), networkDevice.getAssetTag());
            } else {
                eventForwarder.sendAssetFailed(e.getMessage(), networkDevice.getAssetTag());
            }
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

    public NetworkDevice toNetworkDevice(Node node, String parentNodeLabel, String ipaddress, TipoApparato tipoApparato) {
        NetworkDevice networkDevice = new NetworkDevice();
        networkDevice.setSysClassName("u_cmdb_ci_apparati_di_rete");
        networkDevice.setCategoria("Reti Telecomunicazioni");
        networkDevice.setName(node.getLabel());
        networkDevice.setAssetTag(getAssetTag(node));
        networkDevice.setIpAddress(ipaddress);
        networkDevice.setParentalNode(parentNodeLabel);
        networkDevice.setModello(node.getAssetRecord().getOperatingSystem()+":"+node.getAssetRecord().getModelNumber());
        networkDevice.setMarca(node.getAssetRecord().getVendor());
        networkDevice.setModelId("Apparati di Rete");
        networkDevice.setInstallStatus(InstallStatus.ATTIVO);

        //location
        networkDevice.setLocation(getLocation(node));
        networkDevice.setLatitudine(String.valueOf(node.getAssetRecord().getGeolocation().getLatitude()));
        networkDevice.setLongitudine(String.valueOf(node.getAssetRecord().getGeolocation().getLongitude()));

        //specific network device
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
        accessPoint.setModello(node.getAssetRecord().getOperatingSystem()+":"+node.getAssetRecord().getModelNumber());
        accessPoint.setMarca(node.getAssetRecord().getVendor());
        accessPoint.setModelId("Access Point");
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

    private TipoCollegamento getTipoCollegamento(String location) {
        if (location.equals("Default")) {
            return TipoCollegamento.CAMPUS;
        }
        if (location.equals(locationAccessPointSctt)){
            return TipoCollegamento.SCTT;
        }
        return TipoCollegamento.ALTRO;
    }

    public boolean disableAsset(String assetTag) {
        if (networkDeviceMap.containsKey(assetTag)) {
            NetworkDevice nd = toNetworkDevice(networkDeviceMap.get(assetTag));
            if (nd == null) {
                LOG.error("disableAsset: failed to deserialize NetworkDevice for {}", assetTag);
                return false;
            }
            if (nd.getInstallStatus().equals(InstallStatus.DISATTIVO)) {
                LOG.info("disableAsset: Already Disabled NetworkDevice for {}", assetTag);
                return true;
            }
            nd.setInstallStatus(InstallStatus.DISATTIVO);
            sendNetworkDevice(null, nd);
            return true;
        }
        if (accessPointMap.containsKey(assetTag)) {
            AccessPoint ap = toAccessPoint(accessPointMap.get(assetTag));
            if (ap == null) {
                LOG.error("disableAsset: failed to deserialize AccessPoint for {}", assetTag);
                return false;
            }
            if (ap.getInstallStatus().equals(InstallStatus.DISATTIVO)) {
                LOG.info("disableAsset: Already Disabled AccessPoint for {}", assetTag);
                return true;
            }
            ap.setInstallStatus(InstallStatus.DISATTIVO);
            sendAccessPoint(null, ap);
            return true;
        }
        LOG.warn("disableAsset: asset not found in cache: {}", assetTag);
        return false;
    }

    @Override
    public void run() {
        LOG.info("run: calling");
        List<Node> nodes = nodeDao.getNodes().stream().filter(n -> n.getCategories().contains(filter)).toList();
        LOG.info("run: found: {} nodes", nodes.size());
        Set<String> currentAssetTags = nodes.stream().map(AssetForwarder::getAssetTag).collect(Collectors.toSet());
        currentAssetTags.forEach(h -> LOG.debug("run: found: {} hash", h));
        Set<String> cachedDeletedAssetTags = hashCache.keySet().stream()
                .filter(k -> !currentAssetTags.contains(k))
                .collect(Collectors.toSet());
        cachedDeletedAssetTags.forEach(this::disableAsset);
        nodes.forEach(this::sendAsset);
    }

}
