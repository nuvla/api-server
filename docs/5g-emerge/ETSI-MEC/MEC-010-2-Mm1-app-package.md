# ETSI MEC 010-2 Mm1 Reference Point Implementation

## Overview

This document describes the implementation of the **Mm1 reference point** for Application Package Management, as defined in ETSI GS MEC 010-2 v2.2.1.

**Reference Point**: Mm1 (OSS ↔ MEO)  
**Standard**: ETSI GS MEC 010-2 v2.2.1  
**Sections**: 7.3.1 (Operations), 7.3.2 (Data Types)

## Purpose

The Mm1 reference point enables the **Operations Support System (OSS)** to manage application packages in the **Multi-access Edge Orchestrator (MEO)**. This includes:

- Querying available application packages
- Retrieving specific application package details
- Onboarding new application packages
- Deleting application packages

## Reference Point Architecture

```
┌─────────────┐
│             │
│     OSS     │  (Operations Support System)
│             │
└──────┬──────┘
       │
       │ Mm1 (Application Package Management)
       │
┌──────▼──────┐
│             │
│     MEO     │  (Multi-access Edge Orchestrator)
│             │
└─────────────┘
```

## Relationship to Other Reference Points

- **Mm1** (OSS ↔ MEO): Package management (this implementation)
- **Mm3** (Customer ↔ MEO): Application lifecycle management
- **Mm9** (MEO ↔ Package Repository): Package storage/retrieval

Mm1 complements Mm3 by providing OSS-level management capabilities, while Mm3 serves end customers.

## Implementation Mapping

### Nuvla Modules as Application Packages

The implementation maps Nuvla **modules** (subtype: `application_mec`) to MEC **application packages**:

| MEC Concept | Nuvla Equivalent |
|-------------|------------------|
| Application Package (AppPkg) | Module resource (subtype: application_mec) |
| Application Descriptor (AppD) | Module content (ETSI MEC 037 format) |
| Package Repository | Module catalog/database |
| appPkgId | module/[id] |
| appDId | content/appDId or module/[id] |

**Module Subtype**: `application_mec`  
**AppD Format**: ETSI GS MEC 037 v3.2.1  
**Implementation Files**:
- `src/com/sixsq/nuvla/server/resources/mec/app_package.clj` - Mm1 API
- `src/com/sixsq/nuvla/server/resources/module_application_mec.clj` - MEC module CRUD
- `src/com/sixsq/nuvla/server/resources/spec/module_application_mec.cljc` - MEC 037 AppD spec

### Creating MEC Packages

When creating a package via Mm1, the implementation:

1. **Creates a module** with subtype `application_mec`
2. **Validates AppD content** against ETSI MEC 037 spec
3. **Sets initial states**:
   - onboardingState: `CREATED`
   - operationalState: `DISABLED`
   - usageState: `NOT_IN_USE`
4. **Stores metadata** in module content field
5. **Returns AppPkgInfo** with MEC-compliant format

## API Endpoints

### Base Path

```
/mec/app_lcm/v2/app_packages
```

### 1. Query Application Packages

**Endpoint**: `GET /app_packages`  
**ETSI Section**: 7.3.1.3.1  
**Description**: Retrieve list of application packages with optional filtering

#### Query Parameters

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| appPkgId | string | Filter by package ID | `module/abc-123` |
| appDId | string | Filter by app descriptor ID | `module/app-xyz` |
| appName | string | Filter by application name | `My Edge App` |
| appProvider | string | Filter by provider | `Acme Corp` |
| appSoftVersion | string | Filter by software version | `1.2.3` |
| operationalState | enum | ENABLED or DISABLED | `ENABLED` |
| usageState | enum | IN_USE or NOT_IN_USE | `IN_USE` |
| onboardingState | enum | Package state | `ONBOARDED` |

#### Example Request

```bash
GET /mec/app_lcm/v2/app_packages?appProvider=Acme&usageState=IN_USE
```

#### Example Response

```json
{
  "AppPkgInfo": [
    {
      "id": "module/mec-app-123",
      "appPkgId": "module/mec-app-123",
      "appDId": "module/mec-app-123",
      "appName": "Edge AR Application",
      "appProvider": "Acme Corp",
      "appSoftVersion": "2.1.0",
      "appDVersion": "1.0",
      "checksum": {
        "algorithm": "SHA-256",
        "hash": "abc123def456..."
      },
      "operationalState": "ENABLED",
      "usageState": "IN_USE",
      "onboardingState": "ONBOARDED",
      "appPkgPath": "acme/edge-apps/ar-app",
      "created": "2024-01-15T10:00:00Z",
      "updated": "2024-01-20T14:30:00Z"
    }
  ],
  "_links": {
    "self": {
      "href": "/mec/app_lcm/v2/app_packages"
    }
  }
}
```

### 2. Get Application Package

**Endpoint**: `GET /app_packages/{appPkgId}`  
**ETSI Section**: 7.3.1.3.2  
**Description**: Retrieve detailed information about a specific application package

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| appPkgId | string | Yes | Application package identifier |

#### Example Request

```bash
GET /mec/app_lcm/v2/app_packages/module/mec-app-123
```

#### Example Response

```json
{
  "id": "module/mec-app-123",
  "appPkgId": "module/mec-app-123",
  "appDId": "module/mec-app-123",
  "appName": "Edge AR Application",
  "appProvider": "Acme Corp",
  "appSoftVersion": "2.1.0",
  "appDVersion": "1.0",
  "checksum": {
    "algorithm": "SHA-256",
    "hash": "abc123def456..."
  },
  "operationalState": "ENABLED",
  "usageState": "IN_USE",
  "onboardingState": "ONBOARDED",
  "appPkgPath": "acme/edge-apps/ar-app",
  "moduletype": "application_mec",
  "created": "2024-01-15T10:00:00Z",
  "updated": "2024-01-20T14:30:00Z"
}
```

#### Error Responses

**404 Not Found**
```json
{
  "type": "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Application package module/invalid-id not found"
}
```

### 3. Onboard Application Package

**Endpoint**: `POST /app_packages`  
**ETSI Section**: 7.3.1.3.3  
**Description**: Create a new application package (onboarding initiation)

#### Request Body (CreateAppPkg)

```json
{
  "appPkgName": "New Edge Application",
  "appPkgVersion": "1.0.0",
  "appPkgPath": "/packages/new-app",
  "userDefinedData": {
    "department": "edge-services",
    "project": "5g-trial"
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| appPkgName | string | Yes | Name of the application package |
| appPkgVersion | string | No | Version (default: "1.0.0") |
| appPkgPath | string | No | Custom path (auto-generated if omitted) |
| userDefinedData | object | No | Key-value pairs for metadata |

#### Example Request

```bash
POST /mec/app_lcm/v2/app_packages
Content-Type: application/json

{
  "appPkgName": "IoT Gateway App",
  "appPkgVersion": "3.2.1",
  "userDefinedData": {
    "category": "iot",
    "region": "europe"
  }
}
```

#### Example Response (201 Created)

```json
{
  "id": "module/new-pkg-456",
  "appPkgId": "module/new-pkg-456",
  "appDId": "module/new-pkg-456",
  "appName": "IoT Gateway App",
  "appProvider": "user@example.com",
  "appSoftVersion": "3.2.1",
  "appDVersion": "1.0",
  "checksum": {
    "algorithm": "SHA-256",
    "hash": "pending"
  },
  "operationalState": "DISABLED",
  "usageState": "NOT_IN_USE",
  "onboardingState": "CREATED",
  "appPkgPath": "/app_packages/module/new-pkg-456",
  "userDefinedData": {
    "category": "iot",
    "region": "europe"
  },
  "created": "2024-10-24T15:30:00Z",
  "updated": "2024-10-24T15:30:00Z",
  "_links": {
    "self": {
      "href": "/mec/app_lcm/v2/app_packages/module/new-pkg-456"
    },
    "appPkgContent": {
      "href": "/mec/app_lcm/v2/app_packages/module/new-pkg-456/package_content"
    },
    "appD": {
      "href": "/mec/app_lcm/v2/app_packages/module/new-pkg-456/appD"
    }
  }
}
```

**Response Headers**:
```
Location: /mec/app_lcm/v2/app_packages/module/new-pkg-456
```

#### Error Responses

**400 Bad Request**
```json
{
  "type": "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/bad-request",
  "title": "Bad Request",
  "status": 400,
  "detail": "appPkgName is required"
}
```

### 4. Delete Application Package

**Endpoint**: `DELETE /app_packages/{appPkgId}`  
**ETSI Section**: 7.3.1.3.4  
**Description**: Delete an application package

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| appPkgId | string | Yes | Application package identifier |

#### Example Request

```bash
DELETE /mec/app_lcm/v2/app_packages/module/old-pkg-789
```

#### Success Response

**204 No Content** (empty body)

#### Error Responses

**404 Not Found**
```json
{
  "type": "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Application package module/old-pkg-789 not found"
}
```

**409 Conflict**
```json
{
  "type": "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "Application package is currently in use and cannot be deleted"
}
```

## Data Types

### AppPkgInfo (Section 7.3.2.2)

Application package information returned by GET operations.

| Attribute | Type | Cardinality | Description |
|-----------|------|-------------|-------------|
| id | string | 1 | Identifier |
| appPkgId | string | 1 | Application package ID |
| appDId | string | 1 | Application descriptor ID |
| appName | string | 1 | Application name |
| appProvider | string | 1 | Provider name |
| appSoftVersion | string | 1 | Software version |
| appDVersion | string | 1 | Descriptor version |
| checksum | Checksum | 1 | Package checksum |
| operationalState | enum | 1 | ENABLED or DISABLED |
| usageState | enum | 1 | IN_USE or NOT_IN_USE |
| onboardingState | enum | 1 | CREATED, UPLOADING, PROCESSING, ONBOARDED |
| appPkgPath | string | 0..1 | Package path |
| userDefinedData | object | 0..1 | Custom metadata |
| created | timestamp | 1 | Creation time |
| updated | timestamp | 1 | Last update time |

### CreateAppPkg (Section 7.3.2.3)

Request body for POST /app_packages.

| Attribute | Type | Cardinality | Description |
|-----------|------|-------------|-------------|
| appPkgName | string | 1 | Package name |
| appPkgVersion | string | 0..1 | Package version |
| appPkgPath | string | 0..1 | Custom path |
| userDefinedData | object | 0..1 | Custom metadata |

## State Machine

### Onboarding States

```
CREATED → UPLOADING → PROCESSING → ONBOARDED
           ↓            ↓
        [upload]    [process AppD]
```

### Operational States

- **ENABLED**: Package can be instantiated
- **DISABLED**: Package cannot be instantiated

### Usage States

- **IN_USE**: Package has active instances (published=true)
- **NOT_IN_USE**: No active instances (published=false)

## Integration with Nuvla

### Module Query Mapping

```clojure
;; MEC query parameters → Nuvla filter
{:appName "MyApp"} 
→ "content/appName='MyApp' or name='MyApp'"

{:appProvider "Acme"} 
→ "content/appProvider='Acme'"

{:usageState "IN_USE"} 
→ "published=true"
```

### Module Conversion

```clojure
(module->app-pkg-info nuvla-module)
→ MEC AppPkgInfo structure
```

## Usage Examples

### 1. List All Application Packages

```bash
curl -X GET http://nuvla.io/mec/app_lcm/v2/app_packages \
  -H "Accept: application/json"
```

### 2. Find Packages by Provider

```bash
curl -X GET "http://nuvla.io/mec/app_lcm/v2/app_packages?appProvider=Acme%20Corp" \
  -H "Accept: application/json"
```

### 3. Get Package Details

```bash
curl -X GET http://nuvla.io/mec/app_lcm/v2/app_packages/module/mec-app-123 \
  -H "Accept: application/json"
```

### 4. Onboard New Package

```bash
curl -X POST http://nuvla.io/mec/app_lcm/v2/app_packages \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "appPkgName": "Smart City Sensor",
    "appPkgVersion": "2.0.0",
    "userDefinedData": {
      "category": "smart-city",
      "location": "barcelona"
    }
  }'
```

### 5. Delete Package

```bash
curl -X DELETE http://nuvla.io/mec/app_lcm/v2/app_packages/module/old-pkg-789 \
  -H "Accept: application/json"
```

## Standards Compliance

### ETSI GS MEC 010-2 v2.2.1

- ✅ Section 7.3.1.3.1: GET /app_packages (Query)
- ✅ Section 7.3.1.3.2: GET /app_packages/{appPkgId} (Get)
- ✅ Section 7.3.1.3.3: POST /app_packages (Create)
- ✅ Section 7.3.1.3.4: DELETE /app_packages/{appPkgId} (Delete)
- ✅ Section 7.3.2.2: AppPkgInfo data type
- ✅ Section 7.3.2.3: CreateAppPkg data type

### Not Yet Implemented

- Section 7.3.1.3.5: PATCH /app_packages/{appPkgId} (Update package info)
- Section 7.3.1.3.6: GET /app_packages/{appPkgId}/appD (Get AppD content)
- Section 7.3.1.3.7: PUT /app_packages/{appPkgId}/package_content (Upload content)
- Section 7.3.1.3.8: GET /app_packages/{appPkgId}/package_content (Download content)

## Testing

Run unit tests:

```bash
lein test :only com.sixsq.nuvla.server.resources.mec.app-package-test
```

## Files

- Implementation: `code/src/com/sixsq/nuvla/server/resources/mec/app_package.clj`
- Tests: `code/test/com/sixsq/nuvla/server/resources/mec/app_package_test.clj`
- Documentation: `docs/5g-emerge/MEC-010-2-Mm1-app-package.md` (this file)

## Future Enhancements

1. **Package Content Management**
   - Upload package content (PUT /package_content)
   - Download package content (GET /package_content)
   - Extract and validate AppD from package

2. **Package Update**
   - PATCH endpoint for updating package metadata
   - Version management

3. **Advanced Filtering**
   - Complex query expressions
   - Pagination support
   - Sorting

4. **Subscription/Notification**
   - Subscribe to package state changes
   - Webhooks for onboarding events

## References

- [ETSI GS MEC 010-2 v2.2.1](https://www.etsi.org/deliver/etsi_gs/MEC/001_099/01002/02.02.01_60/gs_MEC01002v020201p.pdf)
- [MEC 037 AppD Format](./MEC-037-AppD-module-subtype.md)
- [Mm3 Implementation](./MEC-003-Mm3-implementation.md)
- [Reference Points Compliance](./MEC-reference-points-compliance.md)
