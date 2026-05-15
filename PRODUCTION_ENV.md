# Production environment variables

Minimum set of environment variables for running this service in production.
Defaults shown apply when the variable is not set; **bold** values are
recommended overrides for production.

## Must be set explicitly (no usable default)

| Var | What it is | Example |
|---|---|---|
| `COURTSCHEDULER_SYSTEM_USER_ID` | UUID sent as `CJSCPPUID` on outbound calls | `22222222-2222-2222-2222-222222222222` |
| `CSCHED_ROTASLSTORAGECONNECTIONSTRING` | Azure Storage connection string for rota blobs | `DefaultEndpointsProtocol=https;AccountName=…;AccountKey=…;EndpointSuffix=core.windows.net` |
| `CSCHED_BASE_URL` | Base URL of the CPP gateway fronting referencedata + usersgroups query APIs | `https://cpp-gateway.cpp.svc.cluster.local` |
| `CSCHED_DATASOURCE_URL` | Postgres JDBC URL | `jdbc:postgresql://courtscheduler-db.cpp.svc.cluster.local:5432/courtscheduler` |
| `CSCHED_DATASOURCE_USERNAME` | DB user | `courtscheduler` |
| `CSCHED_DATASOURCE_PASSWORD` | DB password | *(secret)* |
| `CSCHED_ARTEMIS_HOST_PRIMARY` | Artemis broker host | `artemis-primary.cpp.svc.cluster.local` |
| `CSCHED_ARTEMIS_USER` | Artemis user | — |
| `CSCHED_ARTEMIS_PASSWORD` | Artemis password | *(secret)* |
| `REDIS_COMMON_CACHE_HOST` | Redis host (default `localhost` won't work in prod) | `redis-common-cache.cpp.svc.cluster.local` |

## Should be set in prod (defaults exist but are environment-specific)

| Var | Default | Set in prod? |
|---|---|---|
| `SERVER_PORT` | `8083` | usually fine to leave |
| `CSCHED_AUTHZ_ENABLED` | `false` | **`true`** |
| `CSCHED_HTTP_AUDIT_ENABLED` | `false` | **`true`** |
| `CSCHED_AUDIT_ENABLED` | `false` | **`true`** |
| `CSCHED_DB_POOL_SIZE` | `20` | tune to load |
| `CSCHED_DB_MIN_IDLE` | `5` | tune to load |
| `CSCHED_ARTEMIS_PORT` | `61616` | match broker |
| `CSCHED_ARTEMIS_SSL_ENABLED` | `false` | **`true`** |
| `CSCHED_ARTEMIS_HA` | `false` | **`true`** if broker is clustered |
| `CSCHED_ARTEMIS_HOST_SECONDARY` | empty | set if HA |
| `CSCHED_ARTEMIS_KEYSTORE` / `…_KEYSTORE_PASSWORD` | empty | required if SSL enabled |
| `REDIS_COMMON_CACHE_PORT` | `6380` | match cluster |
| `REDIS_COMMON_CACHE_USE_SSL` | `false` | **`true`** for Azure Cache for Redis |
| `REDIS_COMMON_CACHE_KEY` | `courtscheduler` | leave |
| `REDIS_COMMON_CACHE_KEY_TTL` | `86400` (24h) | tune |
| `CSCHED_HTTP_CONNECT_TIMEOUT_SECONDS` | `5` | leave |
| `CSCHED_HTTP_READ_TIMEOUT_SECONDS` | `30` | leave |
| `ROTA_MONTHS_OF_PROVISIONAL_DATA` | `6` | leave unless business says otherwise |
| `ROTA_CYCLE_LENGTH_DAYS` | `28` | leave |
| `CSCHED_ROTASLINPUTCONTAINERNAME` | `schedulelistinginput` | match storage account |
| `CSCHED_ROTASLARCHIVECONTAINERNAME` | `schedulelistingarchive` | match storage account |
| `VIRTUAL_THREADS` | `false` | optional perf knob |

## Don't set in prod

JMS retry knobs, OTLP / tracing toggles, Liquibase enable flags — leave at
their built-in defaults unless you have a specific reason to override.

## Secrets

Always source via the platform's secret manager (e.g. Azure Key Vault,
Kubernetes `Secret`), never as plain environment literals in deployment
manifests:

- `CSCHED_DATASOURCE_PASSWORD`
- `CSCHED_ROTASLSTORAGECONNECTIONSTRING`
- `CSCHED_ARTEMIS_PASSWORD`
- `CSCHED_ARTEMIS_KEYSTORE_PASSWORD`
