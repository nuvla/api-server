# MEC 037 Application Descriptor Module Subtype

## Overview

The MEC 037 Application Descriptor (AppD) module subtype provides native ETSI MEC compliance for application packaging in the Nuvla catalog. It implements the Mm9 (Package Management) reference point using the standardized ETSI GS MEC 037 v3.2.1 format.

## Purpose

This module type enables:
- **Native MEC Compliance**: Applications described using standard ETSI MEC 037 format
- **Resource Requirements**: Precise compute, storage, and memory specifications
- **MEC Service Dependencies**: Declare required MEC services (RNIS, location, etc.)
- **Traffic/DNS Management**: Network configuration via standard descriptors
- **Lifecycle Integration**: Seamless deployment via MEC 010-2 lifecycle API (Mm3)

## Module Structure

### Basic Attributes

```json
{
  "resource-type": "module",
  "subtype": "application_mec",
  "name": "My MEC Application",
  "description": "Example MEC app",
  "path": "examples/mec/my-app",
  "content": { ... }
}
```

### AppD Content (ETSI MEC 037)

The `content` field contains the MEC 037 Application Descriptor:

#### Core Attributes
- `appDId`: Unique application descriptor ID (module ID)
- `appDVersion`: AppD schema version (e.g., "1.0")
- `appName`: Human-readable application name
- `appProvider`: Organization providing the app
- `appSoftVersion`: Application software version
- `mecVersion`: Required MEC platform version (e.g., "3.1.1")

#### Virtual Compute Descriptor
```json
"virtualComputeDescriptor": {
  "virtualCpu": {
    "numVirtualCpu": 4,
    "virtualCpuClock": 2400,
    "virtualCpuPinning": true
  },
  "virtualMemory": {
    "virtualMemSize": 8192,
    "numaEnabled": true
  }
}
```

#### Virtual Storage Descriptor
```json
"virtualStorageDescriptor": [
  {
    "id": "storage-1",
    "typeOfStorage": "BLOCK",
    "sizeOfStorage": 100,
    "rdmaEnabled": false
  }
]
```

#### Software Image Descriptor
```json
"swImageDescriptor": [
  {
    "swImageName": "my-app",
    "swImageVersion": "1.0.0",
    "containerFormat": "DOCKER",
    "swImage": "docker.io/acme/my-app:1.0.0",
    "minDisk": 10,
    "minRam": 2048
  }
]
```

Supported container formats:
- `DOCKER`: Docker containers (currently primary support)
- `ACI`: App Container Image (future support)
- `OCI`: Open Container Initiative (future support)

#### External Connection Points
```json
"appExtCpd": [
  {
    "cpdId": "eth0",
    "layerProtocol": "HTTP",
    "addressType": "IPV4"
  }
]
```

Supported protocols: `TCP`, `UDP`, `HTTP`, `HTTPS`, `WEBSOCKET`  
Address types: `IPV4`, `IPV6`, `MAC`

#### MEC Service Dependencies
```json
"appServiceRequired": [
  {
    "serName": "rnis",
    "version": "2.1.1",
    "transportDependency": {
      "transportType": "REST_HTTP",
      "securityType": "TLS"
    },
    "permissions": ["READ", "WRITE"]
  }
]
```

Available MEC services:
- `rnis`: Radio Network Information Service
- `location`: Location Service
- `ue-identity`: UE Identity Service
- `bandwidth-management`: Bandwidth Management Service
- `wlan-information`: WLAN Information Service
- `fixed-access-information`: Fixed Access Information Service
- `traffic-management`: Traffic Management Service

#### Traffic Rules
```json
"trafficRuleDescriptor": [
  {
    "trafficRuleId": "rule-1",
    "filterType": "HTTP",
    "priority": 10,
    "trafficFilter": {
      "srcAddress": ["0.0.0.0/0"],
      "dstAddress": ["10.0.0.0/8"],
      "dstPort": [8080]
    },
    "action": "FORWARD",
    "dstInterface": "eth0"
  }
]
```

Filter types: `FLOW`, `PACKET`, `HTTP`  
Actions: `DROP`, `FORWARD`, `PASSTHROUGH`  
Priority: 0-255 (higher = more priority)

#### DNS Rules
```json
"dnsRuleDescriptor": [
  {
    "dnsRuleId": "dns-1",
    "domainName": "my-app.mec.local",
    "ipAddressType": "IPV4",
    "ipAddress": "10.0.1.100",
    "ttl": 300
  }
]
```

#### Feature Dependencies
```json
"appFeatureRequired": [
  {
    "featureName": "gpu-acceleration",
    "featureVersion": "1.0"
  }
]
```

#### Latency Requirements
```json
"latencyDescriptor": {
  "maxLatency": 10
}
```

Maximum latency in milliseconds.

## Validation

The module subtype performs comprehensive validation:

### Schema Validation
- All fields validated against ETSI MEC 037 v3.2.1 specification
- Required fields enforced
- Value ranges checked (e.g., priority 0-255)

### Resource Validation
- CPU count: 1-64 cores (warning > 64)
- Memory: 256MB - 256GB (warning > 256GB)
- Storage: 1GB - 10TB per descriptor (warning > 5TB)

### Image Validation
- Container image format: `registry.example.com/app:tag`
- At least one image required
- Supported formats: DOCKER (primary), ACI, OCI

### Network Validation
- IPv4 format: `xxx.xxx.xxx.xxx`
- IPv6 format: valid hex colon notation
- Protocol/port combinations validated

## Usage

### Creating a MEC Module

```bash
POST /api/module
Content-Type: application/json

{
  "subtype": "application_mec",
  "name": "My MEC App",
  "path": "acme/mec-apps/my-app",
  "content": {
    "appDId": "module/...",
    "appName": "My MEC App",
    ...
  }
}
```

### Deploying via Mm3 Lifecycle API

The MEC AppD is automatically converted to deployment parameters:

```clojure
;; Internal conversion
(appd->deployment-params module-id content)
=> {:appDId "module/..."
    :appName "My MEC App"
    :containerImage "docker.io/acme/my-app:1.0"
    :containerFormat "DOCKER"
    :virtualComputeDescriptor {...}
    :appServiceRequired [...]
    :trafficRuleDescriptor [...]
    :dnsRuleDescriptor [...]}
```

These parameters are sent to MEPM via Mm3 client for deployment.

### Checking MEPM Compatibility

```clojure
(check-mec-compatibility appd-content mepm-capabilities)
=> {:compatible? true
    :version-match? true
    :services-match? true
    :missing-services #{}}
```

## Integration Points

### Mm3 (Customer API - MEC 010-2)
- Endpoint: `/app_lcm/v2/app_instances`
- Receives MEC AppD from customers
- Validates requirements
- Triggers lifecycle operations

### Mm3 (MEPM Interface)
- Client: `mm3_client.clj`
- Sends deployment requests with MEC AppD
- MEPM validates against capabilities
- Configures MEP with traffic/DNS rules

### Mm9 (Package Repository)
- Module catalog stores MEC AppDs
- Searchable by MEC services, resources
- Versioned descriptors

## Example

See complete example: [mec-appd-example.json](examples/mec-appd-example.json)

## API Endpoints

```
# Module CRUD
GET    /api/module-application_mec          # List MEC modules
POST   /api/module                          # Create (subtype: application_mec)
GET    /api/module/{id}                     # Retrieve
PUT    /api/module/{id}                     # Update
DELETE /api/module/{id}                     # Delete

# Search by MEC attributes
GET /api/module?filter=subtype="application_mec" and content/mecVersion>="3.1.1"
GET /api/module?filter=subtype="application_mec" and content/appServiceRequired/any(s: s/serName="rnis")
```

## Validation Functions

```clojure
(require '[com.sixsq.nuvla.server.resources.spec.module-application-mec :as mec-spec])

;; Validate AppD content
(mec-spec/valid-mec-appd? appd-content)
=> true

;; Get validation errors
(mec-spec/explain-mec-appd appd-content)
;; Prints detailed errors

;; Get problem data
(mec-spec/mec-appd-problems appd-content)
=> {:problems [...]}
```

## Standards Compliance

This implementation follows:
- **ETSI GS MEC 003 v3.2.1**: Multi-access Edge Computing Framework
- **ETSI GS MEC 010-2 v2.2.1**: Application Lifecycle API
- **ETSI GS MEC 037 v3.2.1**: Application Descriptor Format

## Migration from Docker/K8s Modules

To migrate existing Docker or Kubernetes modules to MEC format:

1. Extract resource requirements from Docker Compose/K8s manifests
2. Map container images to `swImageDescriptor`
3. Identify MEC service dependencies (if any)
4. Add traffic/DNS rules if needed
5. Create new module with `subtype: "application_mec"`

Example conversion utilities will be provided in future releases.

## Limitations

### Current Version
- Primary container support: Docker
- ACI/OCI formats: spec defined, runtime support pending
- Multi-architecture: not yet implemented
- GPU/FPGA acceleration: feature flags only, no orchestration

### Future Enhancements
- OCI/ACI runtime support
- Multi-arch container selection
- GPU resource management
- FPGA orchestration
- Advanced network slicing
- AI/ML workload optimizations

## Testing

Unit tests: `module_application_mec_test.clj`

```bash
lein test :only com.sixsq.nuvla.server.resources.module-application-mec-test
```

## References

- [ETSI MEC 037 v3.2.1 Specification](https://www.etsi.org/deliver/etsi_gs/MEC/001_099/037/03.02.01_60/gs_MEC037v030201p.pdf)
- [MEC 010-2 Lifecycle API](../MEC-010-2-app-lifecycle-api.md)
- [Mm3 Implementation](../MEC-003-Mm3-implementation.md)
- [Reference Points Compliance](../MEC-reference-points-compliance.md)

## Authors

- Nuvla Development Team
- ETSI MEC Working Group standards

## License

Same as Nuvla API Server (Apache 2.0)
