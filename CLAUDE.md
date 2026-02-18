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

OpenNMS plugin (OSGi bundle) that forwards alarms to ServiceNow via REST API. Deployed into Apache Karaf within OpenNMS.

**Modules:**
- `plugin/` — Main source code: alarm forwarding, topology discovery, API client, Karaf shell commands
- `karaf-features/` — Karaf feature descriptor (XML) defining OSGi bundle dependencies
- `assembly/` — KAR (Karaf Archive) packaging for deployment to `/opt/opennms/deploy/`

## Architecture

**Data flow:** OpenNMS alarms → `AlarmForwarder` (filter by UEI + node category) → `EdgeService.getParent()` for topology enrichment → `Alert` DTO → `ApiClientProvider` (OAuth2 token caching) → ServiceNow API

Key components in `org.opennms.plugins.servicenow`:

- **AlarmForwarder** — Implements `AlarmLifecycleListener`. Filters alarms (nodeDown, interfaceDown, nodeLostService/ICMP) by configurable category filter. Converts to Alert and sends via API client.
- **EdgeService** — Scheduled `Runnable` that discovers network topology via `EdgeDao`/`NodeDao`. Builds parent-child relationship maps using depth-limited graph traversal. Provides `getParent()` used by AlarmForwarder.
- **ApiClient / ApiClientProviderImpl** — OkHttp-based HTTP client. Handles OAuth2 client credentials flow with token caching (5s expiry buffer). Supports optional SSL bypass for dev.
- **ConnectionManager** — Stores ServiceNow credentials in OpenNMS `SecureCredentialsVault` under prefix `servicenow_connection_`.
- **WebhookHandlerImpl** — JAX-RS REST endpoint at `/rest/opennms-service-now/ping` (returns "pong").
- **shell/** — Karaf CLI commands for connection management, topology inspection, and test alarm sending.

**Wiring:** OSGi Blueprint (`plugin/src/main/resources/OSGI-INF/blueprint/blueprint.xml`) configures all beans, service references, and properties from `org.opennms.plugins.servicenow.cfg`.

## Tech Stack

- Java 17, Maven 3, Apache Karaf 4.3.10 (OSGi)
- Spring via Aries Blueprint for dependency injection
- OpenNMS Integration API 1.6.0
- OkHttp 4.10.0, Jackson 2.14.1
- Tests: JUnit 4, Mockito 2.18, WireMock 2.35, Awaitility 4.0

## Configuration

Runtime properties are set in `/opt/opennms/etc/org.opennms.plugins.servicenow.cfg` and mapped via Blueprint:
- `filter` — Node category filter (default: "Minnovo")
- `service.initial.delay` / `service.delay` — EdgeService scheduler timing
- `service.iteration` — Max depth for topology discovery
- `service.excluded.fs` — Foreign source to exclude
- `metadata.context` / `metadata.parent.key` / `metadata.gateway.key` — Node metadata keys for topology lookups

## Deployment

```bash
# Copy KAR to OpenNMS
cp assembly/kar/target/opennms-service-now-plugin-*.kar /opt/opennms/deploy/

# Or install via Karaf shell
feature:repo-add mvn:org.opennms.plugins.servicenow/karaf-features/1.1.0/xml
feature:install opennms-plugins-opennms-service-now
```