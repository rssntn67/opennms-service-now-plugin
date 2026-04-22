package org.opennms.plugins.servicenow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.plugins.servicenow.client.ApiClientProvider;
import org.opennms.plugins.servicenow.client.ApiException;
import org.opennms.plugins.servicenow.client.ClientManager;
import org.opennms.plugins.servicenow.connection.ConnectionManager;
import org.opennms.plugins.servicenow.model.AccessPoint;
import org.opennms.plugins.servicenow.model.InstallStatus;
import org.opennms.plugins.servicenow.model.NetworkDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AssetSender {
    private static final Logger LOG = LoggerFactory.getLogger(AssetSender.class);

    private record AccessPointNode(AccessPoint ap, Node n) {
    }

    private record NetworkDeviceNode(NetworkDevice nd, Node n) {
    }

    private final ConnectionManager connectionManager;
    private final ApiClientProvider apiClientProvider;
    private final PluginEventForwarder eventForwarder;
    private final int maxRetry;
    private final long retryDelay;
    private final long timeoutMs;

    private final String hashCacheFile;
    private final String networkDeviceCacheFile;
    private final String accessPointCacheFile;
    private final Map<String, String> hashCache = new HashMap<>();
    private final Map<String, String> accessPointMap = new HashMap<>();
    private final Map<String, String> networkDeviceMap = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final LinkedBlockingQueue<AccessPointNode> apQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<NetworkDeviceNode> ndQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = false;
    private ExecutorService queueThread;
    private final ExecutorService sendThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "asset-forwarder-send"));

    public AssetSender(ConnectionManager connectionManager,
                       ApiClientProvider apiClientProvider,
                       PluginEventForwarder eventForwarder,
                       int maxRetry,
                       long retryDelay,
                       long timeoutMs,
                       String assetCacheFilePrefix) {
        this.connectionManager = connectionManager;
        this.apiClientProvider = apiClientProvider;
        this.eventForwarder = eventForwarder;
        this.maxRetry = maxRetry;
        this.retryDelay = retryDelay;
        this.timeoutMs = timeoutMs;
        this.hashCacheFile = assetCacheFilePrefix + ".properties";
        this.networkDeviceCacheFile = assetCacheFilePrefix + "-NetworkDevice.properties";
        this.accessPointCacheFile = assetCacheFilePrefix + "-AccessPoint.properties";
        loadCache();
        loadNetworkDeviceCache();
        loadAccessPointCache();
    }

    public void enqueue(Node n, AccessPoint ap) {
        apQueue.offer(new AccessPointNode(ap, n));
    }

    public void enqueue(Node n, NetworkDevice nd) {
        ndQueue.offer(new NetworkDeviceNode(nd, n));
    }

    public void start() {
        running = true;
        queueThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "asset-forwarder-queue"));
        queueThread.submit(this::processQueue);
        LOG.info("start: asset sender started (timeoutMs={})", timeoutMs);
    }

    public void stop() {
        running = false;
        if (queueThread != null) {
            queueThread.shutdownNow();
        }
        sendThread.shutdownNow();
        LOG.info("stop: asset sender stopped");
    }

    public boolean isUnchanged(String assetTag, int hash) {
        String cachedHash = hashCache.get(assetTag);
        String newHash = String.valueOf(hash);
        if (newHash.equals(cachedHash)) {
            return true;
        }
        LOG.info("isUnchanged: asset {} changed: cached={}, new={}", assetTag, cachedHash, newHash);
        return false;
    }

    public Set<String> getCachedAssetTags() {
        return hashCache.keySet();
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
            LOG.error("toNetworkDevice: failed to deserialize {}", json, e);
        }
        return null;
    }

    public AccessPoint toAccessPoint(String json) {
        try {
            return objectMapper.readValue(json, AccessPoint.class);
        } catch (JsonProcessingException e) {
            LOG.error("toAccessPoint: failed to deserialize {}", json, e);
        }
        return null;
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
            enqueue(null, nd);
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
            enqueue(null, ap);
            return true;
        }
        LOG.warn("disableAsset: asset not found in cache: {}", assetTag);
        return false;
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
            props.forEach((k, v) -> hashCache.put((String) k, (String) v));
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
            props.forEach((k, v) -> networkDeviceMap.put((String) k, (String) v));
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
            props.forEach((k, v) -> accessPointMap.put((String) k, (String) v));
            LOG.info("loadAccessPointCache: loaded {} entries from {}", accessPointMap.size(), accessPointCacheFile);
        } catch (IOException e) {
            LOG.warn("loadAccessPointCache: failed to read cache file {}, starting fresh", accessPointCacheFile, e);
        }
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

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            LOG.error("toJson: failed to serialize {}", obj, e);
            return "";
        }
    }

    private void processQueue() {
        while (running) {
            try {
                AccessPointNode ap = apQueue.poll(1, TimeUnit.SECONDS);
                if (ap != null) {
                    Future<?> future = sendThread.submit(() -> sendAccessPoint(ap, 0));
                    try {
                        future.get(timeoutMs, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException e) {
                        future.cancel(true);
                        LOG.warn("processQueue: send timed out after {}ms for AccessPoint: {}", timeoutMs, ap);
                        if (ap.n() != null) {
                            eventForwarder.sendAssetFailed(ap.n().getId(), e.getMessage(), ap.ap().getAssetTag());
                        } else {
                            eventForwarder.sendAssetFailed(e.getMessage(), ap.ap().getAssetTag());
                        }
                    } catch (ExecutionException e) {
                        LOG.error("processQueue: send failed for AccessPoint: {}", ap, e.getCause());
                        if (ap.n() != null) {
                            eventForwarder.sendAssetFailed(ap.n().getId(), e.getMessage(), ap.ap().getAssetTag());
                        } else {
                            eventForwarder.sendAssetFailed(e.getMessage(), ap.ap().getAssetTag());
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            try {
                NetworkDeviceNode nd = ndQueue.poll(1, TimeUnit.SECONDS);
                if (nd != null) {
                    Future<?> future = sendThread.submit(() -> sendNetworkDevice(nd, 0));
                    try {
                        future.get(timeoutMs, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException e) {
                        future.cancel(true);
                        LOG.warn("processQueue: send timed out after {}ms for NetworkDevice: {}", timeoutMs, nd);
                        if (nd.n() != null) {
                            eventForwarder.sendAssetFailed(nd.n().getId(), e.getMessage(), nd.nd().getAssetTag());
                        } else {
                            eventForwarder.sendAssetFailed(e.getMessage(), nd.nd().getAssetTag());
                        }
                    } catch (ExecutionException e) {
                        LOG.error("processQueue: send failed for NetworkDevice: {}", nd, e.getCause());
                        if (nd.n() != null) {
                            eventForwarder.sendAssetFailed(nd.n().getId(), e.getMessage(), nd.nd().getAssetTag());
                        } else {
                            eventForwarder.sendAssetFailed(e.getMessage(), nd.nd().getAssetTag());
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void sendAccessPoint(AccessPointNode ap, int retry) {
        AccessPoint accessPoint = ap.ap();
        Node node = ap.n();
        if (maxRetry == retry) {
            LOG.warn("sendAccessPoint: skipping AccessPoint {}, too many retry {}", accessPoint.getAssetTag(), retry);
        }
        retry++;
        LOG.info("sendAccessPoint: sending {}", accessPoint);
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
            LOG.info("sendAccessPoint: forwarded: {}", accessPoint);
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
            try {
                Thread.sleep(retryDelay * retry);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            sendAccessPoint(ap, retry);
        }
    }

    private void sendNetworkDevice(NetworkDeviceNode nd, int retry) {
        NetworkDevice networkDevice = nd.nd();
        Node node = nd.n();
        if (maxRetry == retry) {
            LOG.warn("sendNetworkDevice: skipping NetworkDevice {}, too many retry {}", networkDevice.getAssetTag(), retry);
        }
        retry++;
        LOG.info("sendNetworkDevice: sending {}", networkDevice);
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
            LOG.info("sendNetworkDevice: forwarded: {}", networkDevice);
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
            try {
                Thread.sleep(retryDelay * retry);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            sendNetworkDevice(nd, retry);
        }
    }
}