# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
mvn clean install              # Full build and install
mvn test                       # Run unit tests only (excludes *IT.java)
mvn integration-test           # Run unit + integration tests (*IT.java)
mvn test -pl plugin            # Run tests for the plugin module only
mvn test -pl plugin -Dtest=AlarmForwarderTest  # Run a single test class
```

## Project Overview

OpenNMS plugin (OSGi bundle, version 2.0.0-SNAPSHOT) that forwards alarms and network assets to ServiceNow via REST API. Deployed into Apache Karaf within OpenNMS.

**Modules:**
- `plugin/` — Main source code: alarm forwarding, asset management, topology discovery, API client, Karaf shell commands
- `karaf-features/` — Karaf feature descriptor (XML) defining OSGi bundle dependencies and custom OkHttp/Jackson OSGi features
- `assembly/` — KAR (Karaf Archive) packaging for deployment to `/opt/opennms/deploy/`

## Architecture

**Alarm data flow:** OpenNMS alarms → `AlarmForwarder` (filter by UEI + node category) → `EdgeService.getParent()` for topology enrichment → `AlarmSender` (queued, async, with retry + timeout) → `Alert` DTO → `ApiClientProvider` (OAuth2 token caching) → ServiceNow API

**Asset data flow:** `PluginScheduler` triggers `AssetForwarder` → discovers nodes by category (WiFi, Switch, Firewall, LTE, XDSL) → builds `AccessPoint`/`NetworkDevice` DTOs → `AssetSender` (queued, async, hash-based change detection, retry + timeout) → `ApiClientProvider` → ServiceNow API

Key components in `org.opennms.plugins.servicenow`:

- **AlarmForwarder** — Implements `AlarmLifecycleListener`. Filters alarms (nodeDown, interfaceDown, nodeLostService/ICMP) by configurable category filter. Enqueues matching alarms into `AlarmSender`. Processes alarm snapshot only once on first callback (controlled by `starting` flag).
- **AlarmSender** — Owns the alarm send queue and a dedicated executor pair (queue thread + send thread). Dequeues `AlarmNode` records, converts via `AlarmForwarder.toAlert()`, sends to ServiceNow with configurable retry and per-alarm timeout. Fires `PluginEventForwarder` events on success/failure. Lifecycle managed by Blueprint (`init-method="start"`, `destroy-method="stop"`).
- **AssetForwarder** — Implements `Runnable`. Discovers network assets (AccessPoints, NetworkDevices) from OpenNMS node inventory. Delegates change detection, caching, and sending to `AssetSender`. Static helpers `toNetworkDevice(Node,…)` and `toAccessPoint(Node,…,locationSctt)` build DTOs from node data. Exposes shell-accessible methods (`clearCache`, `getNetworkDeviceCache`, `getAccessPointCache`, `disableAsset`) that delegate to `AssetSender`.
- **AssetSender** — Owns separate AP and ND send queues and a dedicated executor pair. Maintains the three-tier cache: hash cache for change detection (`isUnchanged`), JSON caches for NetworkDevice and AccessPoint data. All caches persisted to disk at `asset.cache.file.prefix`. Handles retry and per-asset timeout. Exposes `clearCache()`, `getCachedAssetTags()`, `getNetworkDeviceCache()`, `getAccessPointCache()`, `disableAsset()`. Lifecycle managed by Blueprint.
- **EdgeService** — Discovers network topology via `EdgeDao`/`NodeDao`. Builds parent-child relationship maps using depth-limited graph traversal and `TopologyEdge.EndpointVisitor` pattern. Provides `getParent()` used by AlarmForwarder.
- **PluginScheduler** — Implements `HealthCheck`. Schedules both `EdgeService` and `AssetForwarder` with configurable delays. AssetForwarder starts 10× later than EdgeService to allow topology to be built first.
- **ApiClient / ApiClientProviderImpl** — OkHttp-based HTTP client. Handles OAuth2 client credentials flow with token caching (5s expiry buffer). Supports optional SSL bypass for dev. Sends `Alert`, `NetworkDevice`, and `AccessPoint` payloads.
- **ClientManager** — Wraps `ApiClientProvider`, validates connections, converts `Connection` to `ApiClientCredentials`.
- **ConnectionManager** — Stores ServiceNow credentials in OpenNMS `SecureCredentialsVault` (optional OSGi reference) under prefix `servicenow_connection_`.
- **EventConfExtension** — Loads custom OpenNMS events from `plugin.ext.events.xml`: sendEventSuccessful, sendEventFailed, sendAssetSuccessful, sendAssetFailed.
- **WebhookHandlerImpl** — JAX-RS REST endpoint at `/rest/opennms-service-now/ping` (returns "pong").
- **shell/** — Karaf CLI commands (scope: `opennms-service-now`). See Shell Commands section below.

**Wiring:** OSGi Blueprint (`plugin/src/main/resources/OSGI-INF/blueprint/blueprint.xml`) configures all beans, service references, and properties from `org.opennms.plugins.servicenow.cfg`.

## Data Models (`org.opennms.plugins.servicenow.model`)

- **Alert** — Alarm DTO with fields: id, time, source, type, severity, description, node, asset, status, parentalNodeLabel. Contains nested `Severity` and `Status` enums.
- **NetworkDevice** — Asset DTO for switches/firewalls/modems. Fields: modelId, categoria, sysClassName, assetTag, name, marca, modello, location, coordinates, ipAddress, parentalNode, installStatus, tipoApparato.
- **AccessPoint** — Asset DTO for WiFi APs. Extends NetworkDevice fields, adds tipoCollegamento and serialNumber.
- **TipoApparato** — Enum: `SWITCH`, `MODEM_LTE`, `MODEM_XDSL`, `FIREWALL`. Custom Jackson serializer.
- **TipoCollegamento** — Enum: `CAMPUS`, `SCTT`, `ALTRO`. Custom Jackson serializer.
- **InstallStatus** — Enum: `ATTIVO="1"`, `DISATTIVO="7"`, `SOSPESO="100"`. Custom Jackson number serializer.
- **TokenResponse** — OAuth2 token response DTO.

## Shell Commands

All commands use `@Command(scope = "opennms-service-now", name = "...")`, implement `Action`, inject OSGi services via `@Reference`, and use `ShellTable` for formatted output.

**Alarm commands:**
- `send-down-alarm` — Sends a test nodeDown alarm to ServiceNow
- `send-up-alarm` — Sends a test nodeUp alarm to ServiceNow

**Asset commands:**
- `send-asset <foreignSource> <foreignId> <label> <parentLabel> <location> <ipAddress> <category>` — Sends a test asset (category: Wifi, Switch, Firewall, ModemLte, ModemXdsl)
- `get-asset-cache` — Prints asset cache table (ForeignSource, ForeignId, Label, Type, Detail, ParentLabel)
- `clear-asset-cache` — Wipes in-memory caches and deletes all cache files from disk
- `disable-asset <foreignSource> <foreignId>` — Marks an asset as DISATTIVO in ServiceNow

**Topology commands:**
- `get-edge-map` — Displays the topology edge map
- `get-locations` — Lists node locations
- `get-parent <nodeId>` — Gets the parent node for a given node
- `get-gateways` — Lists gateway nodes
- `edge-service-run` — Manually triggers an EdgeService run

**Connection commands** (`shell/connection/`):
- `add-connection` — Adds ServiceNow credentials to the vault
- `get-connection` — Displays the current connection settings
- `delete-connection` — Removes connection from the vault
- `validate-connection` — Tests the connection via OAuth2 token endpoint

## Tech Stack

- Java 17, Maven 3, Apache Karaf 4.3.10 (OSGi)
- Aries Blueprint for dependency injection
- OpenNMS Integration API 1.6.1
- OkHttp 4.10.0, Jackson 2.14.1
- Tests: JUnit 4.13.2, Mockito 2.18, WireMock 2.35.1, JSONAssert 1.5, Awaitility 4.0

## Testing Patterns

- **Unit tests** (`*Test.java`): Mock OpenNMS DAOs/services with Mockito. Test data uses OpenNMS immutable builders (`ImmutableAlarm.newBuilder()`, `ImmutableNode.newBuilder()`). Static helper methods like `getAlarm()`, `getNode()` provide reusable test fixtures.
- **Integration tests** (`*IT.java`): Use WireMock (`@Rule WireMockRule`) to stub the ServiceNow REST API (OAuth token + alert endpoints). Use Awaitility for async verification. Tests marked `@Ignore` are for manual validation against real ServiceNow endpoints.

## Configuration

Runtime properties are set in `/opt/opennms/etc/org.opennms.plugins.servicenow.cfg` and mapped via Blueprint:

| Property | Default | Description |
|---|---|---|
| `filter` | `Minnovo` | Node category filter for alarms |
| `filter.accesspoint` | `Wifi` | Category for WiFi access points |
| `filter.networkdevice.switch` | `Switch` | Category for switches |
| `filter.networkdevice.firewall` | `Firewall` | Category for firewalls |
| `filter.networkdevice.modem.lte` | `LTE` | Category for LTE modems |
| `filter.networkdevice.modem.xdsl` | `XDSL` | Category for xDSL modems |
| `retry` | `3` | Max send attempts (alarms and assets) |
| `retry.delay` | `250` | Base retry delay (ms); multiplied by attempt number |
| `send.timeout` | `30000` | Per-send timeout (ms); cancels the attempt if exceeded (alarms and assets) |
| `token.endpoint` | `token` | OAuth2 token path |
| `alert.endpoint` | *(ServiceNow alert path)* | REST path for sending alerts |
| `asset.endpoint` | *(ServiceNow asset path)* | REST path for sending assets |
| `service.initial.delay` | `5000` | Scheduler initial delay (ms) |
| `service.delay` | `3600000` | Scheduler period (ms, 1 hour) |
| `service.iteration` | `10` | Max depth for topology discovery |
| `service.excluded.fs` | `NODO` | Foreign source to exclude |
| `metadata.context` | `requisition` | Node metadata context |
| `metadata.parent.key` | `parent` | Metadata key for parent node |
| `metadata.gateway.key` | `gateway` | Metadata key for gateway node |
| `asset.cache.file.prefix` | `/opt/opennms/etc/servicenow-asset-cache` | Prefix path for cache files on disk |

## Deployment

```bash
# Copy KAR to remote OpenNMS server
scp assembly/kar/target/opennms-service-now-plugin-*.kar tecnico@opennms.campus.comune.milano.it:/home/tecnico/KAR-SERVICENOW

# Or install via Karaf shell (use current version from pom.xml)
feature:repo-add mvn:org.opennms.plugins.servicenow/karaf-features/<version>/xml
feature:install opennms-plugins-opennms-service-now
```