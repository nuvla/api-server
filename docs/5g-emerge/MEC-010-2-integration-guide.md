# MEC 010-2 Integration Guide
## Integrating with Nuvla MEO Application Lifecycle APIs

**Version:** 1.0  
**Date:** 21 October 2025  
**Project:** 5G-EMERGE / Nuvla.io  
**Standard:** ETSI GS MEC 010-2 v2.2.1

---

## Table of Contents

1. [Overview](#overview)
2. [Authentication](#authentication)
3. [Getting Started](#getting-started)
4. [Application Lifecycle Management](#application-lifecycle-management)
5. [Subscription & Notifications](#subscription--notifications)
6. [MEPM Integration (Mm3 Interface)](#mepm-integration-mm5-interface)
7. [Query Filtering](#query-filtering)
8. [Error Handling](#error-handling)
9. [Best Practices](#best-practices)
10. [Example Workflows](#example-workflows)
11. [Troubleshooting](#troubleshooting)

---

## Overview

Nuvla implements the MEC 010-2 Application Lifecycle Management APIs as a **MEO (MEC Orchestrator)**. This guide explains how to integrate external systems with Nuvla's MEC APIs.

### API Endpoints

**Base URL**: `https://nuvla.io/api`

**Available APIs**:
- Application instance management (CRUD)
- Lifecycle operations (instantiate, terminate, operate)
- Operation occurrence tracking
- Subscription and notifications

### Prerequisites

- Active Nuvla account
- API authentication credentials
- HTTPS-capable client
- (Optional) Webhook endpoint for notifications

---

## Authentication

All MEC API endpoints require Bearer token authentication.

### Step 1: Obtain Session Token

```bash
# Login to Nuvla
curl -X POST https://nuvla.io/api/session \
  -H "Content-Type: application/json" \
  -d '{
    "sessionTemplate": {
      "href": "session-template/api-key",
      "key": "YOUR_API_KEY",
      "secret": "YOUR_API_SECRET"
    }
  }'
```

**Response**:
```json
{
  "resource-id": "session/abc-123",
  "token": "eyJhbGciOiJFUzI1NiJ9..."
}
```

### Step 2: Use Token in Requests

Include the token in the `Authorization` header:

```bash
curl -X GET https://nuvla.io/api/app_lcm/v2/app_instances \
  -H "Authorization: Bearer eyJhbGciOiJFUzI1NiJ9..."
```

### Token Lifecycle

- **Validity**: Tokens are valid for 24 hours by default
- **Renewal**: Request a new token before expiration
- **Revocation**: Logout to invalidate token: `DELETE /session/{id}`

---

## Getting Started

### 1. Verify API Access

Test connectivity and authentication:

```bash
curl -X GET https://nuvla.io/api/app_lcm/v2/app_instances \
  -H "Authorization: Bearer YOUR_TOKEN"
```

Expected response: `200 OK` with empty or populated list.

### 2. Create Your First Application Instance

```bash
curl -X POST https://nuvla.io/api/app_lcm/v2/app_instances \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "appDId": "module/my-app-descriptor",
    "appName": "my-first-app",
    "appDescription": "Test application"
  }'
```

**Response** (`201 Created`):
```json
{
  "id": "deployment/app-001",
  "appInstanceId": "deployment/app-001",
  "appDId": "module/my-app-descriptor",
  "appName": "my-first-app",
  "instantiationState": "NOT_INSTANTIATED",
  "_links": {
    "self": {"href": "/app_lcm/v2/app_instances/deployment/app-001"},
    "instantiate": {"href": "/app_lcm/v2/app_instances/deployment/app-001/instantiate"}
  }
}
```

### 3. Instantiate the Application

```bash
curl -X POST https://nuvla.io/api/app_lcm/v2/app_instances/deployment/app-001/instantiate \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}'
```

**Response** (`202 Accepted`):
```json
{
  "lcmOpOccId": "job/op-001",
  "operationType": "INSTANTIATE",
  "operationState": "STARTING",
  "appInstanceId": "deployment/app-001",
  "startTime": "2025-10-21T10:00:00Z"
}
```

### 4. Track Operation Status

```bash
curl -X GET https://nuvla.io/api/app_lcm/v2/app_lcm_op_occs/job/op-001 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

Poll this endpoint until `operationState` becomes `COMPLETED` or `FAILED`.

---

## Application Lifecycle Management

### Complete Lifecycle

```
NOT_INSTANTIATED
       ↓ instantiate
  INSTANTIATED (STARTED)
       ↓ operate (STOP)
  INSTANTIATED (STOPPED)
       ↓ operate (START)
  INSTANTIATED (STARTED)
       ↓ terminate
NOT_INSTANTIATED
```

### Instantiate Operation

**Purpose**: Deploy application to MEC host

**Prerequisites**:
- Application instance in `NOT_INSTANTIATED` state
- Valid application descriptor (module)
- Sufficient resources on available MEPMs

**Request**:
```bash
POST /app_lcm/v2/app_instances/{id}/instantiate
Content-Type: application/json

{
  "grantId": "optional-grant-id"
}
```

**MEO Behavior**:
1. Validates application descriptor
2. Selects appropriate MEPM based on resource requirements
3. Delegates instantiation to MEPM via Mm5
4. Creates job to track operation
5. Returns operation occurrence ID

**Success Response**: `202 Accepted` with AppLcmOpOcc

### Terminate Operation

**Purpose**: Undeploy application and release resources

**Prerequisites**:
- Application instance in `INSTANTIATED` state

**Request**:
```bash
POST /app_lcm/v2/app_instances/{id}/terminate
Content-Type: application/json

{
  "terminationType": "GRACEFUL"
}
```

**Termination Types**:
- `GRACEFUL`: Allow application to shut down gracefully
- `FORCEFUL`: Immediate termination

**Success Response**: `202 Accepted` with AppLcmOpOcc

### Operate Operation

**Purpose**: Change operational state (START/STOP) without undeployment

**Prerequisites**:
- Application instance in `INSTANTIATED` state

**Request**:
```bash
POST /app_lcm/v2/app_instances/{id}/operate
Content-Type: application/json

{
  "changeStateTo": "STARTED"
}
```

**Valid States**:
- `STARTED`: Start the application
- `STOPPED`: Stop the application

**Success Response**: `202 Accepted` with AppLcmOpOcc

---

## Subscription & Notifications

### Overview

Subscribe to application state changes to receive real-time notifications via webhooks.

**Notification Types**:
1. `AppInstanceStateChangeNotification`: Operational/instantiation state changes
2. `AppLcmOpOccStateChangeNotification`: Operation status changes

### Create Subscription

```bash
curl -X POST https://nuvla.io/api/app_lcm/v2/subscriptions \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "subscriptionType": "AppInstanceStateChangeNotification",
    "callbackUri": "https://my-app.example.com/notifications",
    "appInstanceFilter": {
      "appName": "my-app",
      "operationalState": "STARTED"
    }
  }'
```

### Webhook Setup

Your webhook endpoint must:
- Accept HTTP POST requests
- Return `2xx` status code for successful delivery
- Be accessible via HTTPS (recommended)
- Handle duplicate notifications (use notification ID for deduplication)

**Webhook Request Format**:
```http
POST /notifications HTTP/1.1
Host: my-app.example.com
Content-Type: application/json

{
  "notificationId": "notif-123",
  "notificationType": "AppInstanceStateChangeNotification",
  "subscriptionId": "subscription-456",
  "timestamp": "2025-10-21T10:00:00Z",
  "appInstanceId": "deployment/app-001",
  "changeType": "OPERATIONAL_STATE",
  "operationalState": "STARTED",
  "previousState": "STOPPED",
  "_links": {
    "appInstance": {"href": "/app_lcm/v2/app_instances/deployment/app-001"},
    "subscription": {"href": "/app_lcm/v2/subscriptions/subscription-456"}
  }
}
```

### Retry Logic

Nuvla implements automatic retry for failed webhook deliveries:
- **Attempts**: 3 total (1 initial + 2 retries)
- **Backoff**: Exponential (2s, 4s, 8s, max 30s)
- **Timeout**: 30 seconds per attempt
- **Failure**: After all retries exhausted, notification is dropped

### Filter Matching

Subscriptions support filtering to receive only relevant notifications:

**AppInstanceStateChangeNotification filters**:
- `appInstanceId`: Specific instance ID
- `appName`: Filter by app name
- `operationalState`: STARTED, STOPPED
- `instantiationState`: NOT_INSTANTIATED, INSTANTIATED

**AppLcmOpOccStateChangeNotification filters**:
- `appInstanceId`: Specific instance ID
- `operationType`: INSTANTIATE, TERMINATE, OPERATE
- `operationState`: STARTING, PROCESSING, COMPLETED, FAILED

**Empty filter**: Matches all events (wildcard)

### List and Manage Subscriptions

```bash
# List your subscriptions
GET /app_lcm/v2/subscriptions

# Get specific subscription
GET /app_lcm/v2/subscriptions/{id}

# Delete subscription
DELETE /app_lcm/v2/subscriptions/{id}
```

---

## MEPM Integration (Mm3 Interface)

### Overview

Nuvla MEO delegates actual application deployment to MEPMs (MEC Platform Managers) via the Mm3 interface.

### MEPM Requirements

To integrate as a MEPM with Nuvla, implement these Mm5 operations:

#### 1. Capability Query

**Endpoint**: `GET /mm5/capabilities`

**Purpose**: MEO queries MEPM capabilities to determine if it can host an application

**Response**:
```json
{
  "supports-app-instantiation": true,
  "supported-app-formats": ["docker", "kubernetes"],
  "capabilities": {
    "cpu": 16,
    "memory": 32768,
    "storage": 1024000,
    "gpu": 2
  }
}
```

#### 2. Resource Availability

**Endpoint**: `GET /mm5/resources`

**Purpose**: Query current resource availability

**Response**:
```json
{
  "available": {
    "cpu": 8,
    "memory": 16384,
    "storage": 500000,
    "gpu": 1
  },
  "total": {
    "cpu": 16,
    "memory": 32768,
    "storage": 1024000,
    "gpu": 2
  }
}
```

#### 3. App Instantiation

**Endpoint**: `POST /mm5/app-instances`

**Purpose**: Deploy application instance

**Request**:
```json
{
  "app-instance-id": "deployment/app-001",
  "grant-id": "grant-123",
  "app-descriptor": {
    "image": "my-app:latest",
    "resources": {
      "cpu": 2,
      "memory": 4096
    }
  }
}
```

**Response** (`202 Accepted`):
```json
{
  "instance-id": "local-app-001",
  "status": "deploying"
}
```

#### 4. App Status Query

**Endpoint**: `GET /mm5/app-instances/{id}`

**Purpose**: Query application status

**Response**:
```json
{
  "instance-id": "local-app-001",
  "status": "running",
  "operational-state": "STARTED"
}
```

#### 5. App Termination

**Endpoint**: `DELETE /mm5/app-instances/{id}`

**Purpose**: Undeploy application

**Response**: `204 No Content`

### MEPM Selection Algorithm

Nuvla MEO selects MEPM using these criteria:

1. **Resource Requirements**: Filter MEPMs that meet minimum CPU, memory, GPU
2. **Capability Matching**: Check support for required app format (Docker, K8s)
3. **Availability**: Prefer MEPMs with more available resources
4. **First Match**: Select first MEPM that passes all filters

**Future**: Advanced placement with latency, affinity rules, load balancing

---

## Query Filtering

### FIQL-like Filter Syntax

All list endpoints support FIQL-like filter expressions:

**Format**: `(operator,field,value)`

**Supported Operators**:
- `eq`: Equal
- `neq`: Not equal
- `gt`: Greater than
- `lt`: Less than
- `gte`: Greater than or equal
- `lte`: Less than or equal
- `in`: Set membership (value1,value2,...)
- `and`: Logical AND
- `or`: Logical OR

### Filter Examples

```bash
# Simple equality
GET /app_lcm/v2/app_instances?filter=(eq,appName,my-app)

# Comparison
GET /app_lcm/v2/app_instances?filter=(gt,cpu,2)

# Set membership
GET /app_lcm/v2/app_instances?filter=(in,operationalState,STARTED,STOPPED)

# Logical AND
GET /app_lcm/v2/app_instances?filter=(and,(eq,appName,web-app),(eq,operationalState,STARTED))

# Logical OR
GET /app_lcm/v2/app_instances?filter=(or,(eq,appName,app1),(eq,appName,app2))

# Complex nested
GET /app_lcm/v2/app_lcm_op_occs?filter=(and,(eq,operationType,INSTANTIATE),(or,(eq,operationState,COMPLETED),(eq,operationState,FAILED)))
```

### Pagination

```bash
GET /app_lcm/v2/app_instances?page=1&size=20
```

**Parameters**:
- `page`: Page number (1-based, default 1)
- `size`: Page size (max 100, default 20)

**Response includes HAL links**:
```json
{
  "items": [...],
  "total": 50,
  "page": 1,
  "size": 20,
  "totalPages": 3,
  "_links": {
    "self": {"href": "...?page=1&size=20"},
    "next": {"href": "...?page=2&size=20"},
    "last": {"href": "...?page=3&size=20"}
  }
}
```

### Field Selection

Request only specific fields:

```bash
GET /app_lcm/v2/app_instances?fields=appName,operationalState,instantiationState
```

**Note**: `id` field is always included

---

## Error Handling

All errors follow **RFC 7807 ProblemDetails** format.

### Error Response Structure

```json
{
  "type": "https://docs.nuvla.io/mec/errors/not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "AppInstance app-123 not found",
  "instance": "app-123"
}
```

### Common Error Types

| Status | Type | Description |
|--------|------|-------------|
| 400 | validation-error | Invalid request data |
| 401 | unauthorized | Authentication required |
| 403 | forbidden | Insufficient permissions |
| 404 | not-found | Resource not found |
| 409 | conflict | Resource conflict (e.g., invalid state) |
| 409 | invalid-state | Operation not allowed in current state |
| 422 | operation-not-allowed | Operation not permitted |
| 500 | internal-error | Server error |
| 502 | mepm-error | MEPM communication failure |
| 503 | service-unavailable | Service temporarily down |
| 504 | timeout | Gateway timeout |

### Error Handling Best Practices

1. **Check Status Code**: Always check HTTP status before parsing response
2. **Parse ProblemDetails**: Use `type`, `title`, `detail` for user messages
3. **Use Instance URI**: Track specific error occurrences
4. **Retry Strategy**:
   - 4xx errors: Don't retry (client error)
   - 5xx errors: Retry with exponential backoff
   - 503: Check `Retry-After` header if present

---

## Best Practices

### 1. Authentication

- **Secure Storage**: Store tokens securely (encrypted, memory-only)
- **Token Refresh**: Implement automatic token renewal
- **Session Management**: Logout (DELETE /session) when done

### 2. Error Handling

- **Graceful Degradation**: Handle errors gracefully
- **Logging**: Log all error responses with correlation IDs
- **User Feedback**: Provide actionable error messages

### 3. Asynchronous Operations

- **Polling**: Poll operation status with reasonable intervals (5-10s)
- **Timeouts**: Set appropriate timeouts (5-10 minutes for lifecycle ops)
- **Subscriptions**: Use subscriptions instead of polling when possible

### 4. Resource Management

- **Cleanup**: Always terminate instances when done
- **Quotas**: Monitor resource usage against quotas
- **Idempotency**: Handle duplicate operations gracefully

### 5. Webhooks

- **Security**: Use HTTPS, validate sender
- **Deduplication**: Use notification ID for deduplication
- **Retry Handling**: Implement proper retry logic on your side
- **Monitoring**: Monitor webhook delivery failures

---

## Example Workflows

### Workflow 1: Deploy and Start Application

```bash
# 1. Create app instance
APP_ID=$(curl -X POST https://nuvla.io/api/app_lcm/v2/app_instances \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"appDId":"module/my-app","appName":"test-app"}' \
  | jq -r '.appInstanceId')

# 2. Instantiate
OP_ID=$(curl -X POST https://nuvla.io/api/app_lcm/v2/app_instances/$APP_ID/instantiate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}' \
  | jq -r '.lcmOpOccId')

# 3. Wait for completion
while true; do
  STATE=$(curl -X GET https://nuvla.io/api/app_lcm/v2/app_lcm_op_occs/$OP_ID \
    -H "Authorization: Bearer $TOKEN" \
    | jq -r '.operationState')
  
  if [ "$STATE" == "COMPLETED" ]; then
    echo "Instantiation completed"
    break
  elif [ "$STATE" == "FAILED" ]; then
    echo "Instantiation failed"
    exit 1
  fi
  
  sleep 5
done

# 4. App is now STARTED
```

### Workflow 2: Stop, Start, and Terminate

```bash
# 1. Stop app
curl -X POST https://nuvla.io/api/app_lcm/v2/app_instances/$APP_ID/operate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"changeStateTo":"STOPPED"}'

# 2. Wait for operation to complete (polling omitted for brevity)

# 3. Start app
curl -X POST https://nuvla.io/api/app_lcm/v2/app_instances/$APP_ID/operate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"changeStateTo":"STARTED"}'

# 4. Terminate app
curl -X POST https://nuvla.io/api/app_lcm/v2/app_instances/$APP_ID/terminate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"terminationType":"GRACEFUL"}'

# 5. Wait for termination to complete

# 6. Delete instance
curl -X DELETE https://nuvla.io/api/app_lcm/v2/app_instances/$APP_ID \
  -H "Authorization: Bearer $TOKEN"
```

### Workflow 3: Subscribe to Notifications

```bash
# 1. Create subscription
SUB_ID=$(curl -X POST https://nuvla.io/api/app_lcm/v2/subscriptions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "subscriptionType": "AppInstanceStateChangeNotification",
    "callbackUri": "https://my-app.example.com/notifications",
    "appInstanceFilter": {"appName": "my-app"}
  }' \
  | jq -r '.subscriptionId')

# 2. Your webhook receives notifications when app state changes

# 3. Delete subscription when done
curl -X DELETE https://nuvla.io/api/app_lcm/v2/subscriptions/$SUB_ID \
  -H "Authorization: Bearer $TOKEN"
```

---

## Troubleshooting

### Issue: 401 Unauthorized

**Cause**: Invalid or expired token

**Solution**:
1. Verify token is included in `Authorization: Bearer {token}` header
2. Check token expiration
3. Request new token via `/session`

### Issue: 404 Not Found

**Cause**: Resource doesn't exist or incorrect ID

**Solution**:
1. Verify resource ID is correct
2. Check resource was created successfully
3. Ensure you have permissions to access resource

### Issue: 409 Conflict - Invalid State

**Cause**: Operation not allowed in current state

**Solution**:
1. Check current `instantiationState` and `operationalState`
2. Ensure prerequisites are met (e.g., instantiate before operate)
3. Review state transition diagram

### Issue: 502 Bad Gateway - MEPM Error

**Cause**: MEPM communication failure

**Solution**:
1. Verify MEPM is online and accessible
2. Check Mm3 interface implementation
3. Review MEPM logs for errors
4. Retry operation after delay

### Issue: Webhook Not Receiving Notifications

**Cause**: Network, authentication, or configuration issue

**Solution**:
1. Verify webhook URL is accessible via HTTPS
2. Check webhook returns `2xx` status
3. Review subscription filter (may not match events)
4. Check webhook implementation handles POST requests
5. Monitor Nuvla logs for delivery failures

### Issue: Operation Stuck in PROCESSING

**Cause**: Long-running operation or MEPM issue

**Solution**:
1. Check operation has reasonable timeout (5-10 minutes)
2. Verify MEPM is processing request
3. Review MEPM logs
4. Contact Nuvla support if stuck > 10 minutes

---

## Additional Resources

- **OpenAPI Specification**: `docs/5g-emerge/mec-010-2-openapi.yaml`
- **MEC 010-2 Standard**: https://www.etsi.org/deliver/etsi_gs/MEC/001_099/01002/
- **RFC 7807 (ProblemDetails)**: https://tools.ietf.org/html/rfc7807
- **Nuvla Documentation**: https://docs.nuvla.io/
- **Support**: support@sixsq.com

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 21 Oct 2025 | Initial release |

---

**Document Status**: Production Ready  
**Audience**: External integrators, MEPM developers, API consumers
