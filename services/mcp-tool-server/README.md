# mcp-tool-server

MCP tool server for TicketFlow. It publishes the platform's microservice capabilities as [Model Context Protocol](https://modelcontextprotocol.io) tools so the AI Assistant can discover and call them.

The server is an integration layer. Booking rules, inventory, payment capture and notification delivery stay in their own services. This process validates tool arguments, applies a confirmation gate for consequential calls, and forwards the call over HTTP.

## Stack

| Piece | Choice |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.8 (parent POM) |
| MCP SDK | `io.modelcontextprotocol.sdk:mcp` 2.1.0 |
| Transport | Streamable HTTP on `POST/GET/DELETE /mcp` |
| HTTP client | Spring `RestClient` over the JDK `HttpClient` |
| Port | 8090 |

The official SDK's Spring WebMVC transport now lives in Spring AI. This module uses the SDK's Jakarta Servlet Streamable HTTP transport and registers it beside Spring MVC, which keeps the tool server on the SDK and avoids pulling the AI Assistant stack into this process.

## Layout

```
com.ticketflow.mcptoolserver
  config/       MCP transport, servlet registration, configuration properties
  tools/        Tool schemas, argument checks, MCP result mapping
  services/     Per-service integration methods used by the tools
  clients/      One HTTP client per microservice, plus the shared caller
  security/     Authorization header extracted from the MCP request
  exceptions/   Error codes returned to the assistant
```

Adding a tool means a new `@Bean` of type `McpTool`, a method on the matching integration service, and an operation entry under `ticketflow.services`. Removing a tool is the reverse. Tools are registered from the Spring context, so there is no separate tool list to keep in sync.

## Run

From the repository root, with JDK 21:

```bash
./mvnw -pl services/mcp-tool-server spring-boot:run
```

Health: `GET http://localhost:8090/actuator/health`

Swagger UI: `http://localhost:8090/swagger-ui.html`

OpenAPI document: `http://localhost:8090/v3/api-docs`

MCP endpoint: `http://localhost:8090/mcp`

Swagger documents the HTTP surface of this process. `GET /api/tools` lists the registered MCP tools and their input schemas. Tool calls still go through Streamable HTTP at `/mcp`. Swagger does not execute a tool and does not describe unconfirmed downstream service contracts.

The AI Assistant, or MCP Inspector, connects with Streamable HTTP. There is no stdio mode.

```bash
npx @modelcontextprotocol/inspector
```

In the inspector, set the transport to Streamable HTTP and the URL to `http://localhost:8090/mcp`. If the inspector is opened from a browser origin other than `http://localhost` or `http://127.0.0.1`, add that origin to `ticketflow.mcp.allowed-origins`. Requests whose `Host` header is outside `ticketflow.mcp.allowed-hosts` are rejected with HTTP 421. That check is the SDK's DNS-rebinding protection.

Docker, from the repository root:

```bash
docker compose -f infra/docker-compose.yml up --build mcp-tool-server
```

Tests, including WireMock scenarios that do not need the other services:

```bash
./mvnw -pl services/mcp-tool-server test
```

## Configuration

Service URLs and endpoint paths come from configuration. Empty URLs are allowed: a tool whose service has no URL returns `SERVICE_NOT_CONFIGURED` and does not open a connection.

| Variable | Purpose | Default |
|---|---|---|
| `SERVER_PORT` | HTTP port | `8090` |
| `EVENT_SERVICE_URL` | Event Service base URL | empty |
| `TICKET_SERVICE_URL` | Ticket Service base URL | empty |
| `BOOKING_SERVICE_URL` | Booking Service base URL | empty |
| `PAYMENT_SERVICE_URL` | Payment Service base URL | empty |
| `CUSTOMER_SERVICE_URL` | Customer Service base URL | `http://localhost:8081` |
| `NOTIFICATION_SERVICE_URL` | Notification Service base URL | empty |

`customer-service` is the only business service in this repository. Its README documents port 8081. Its `application.properties` does not set `server.port`, so a locally launched process listens on Spring Boot's default port 8080 unless that is changed. Point `CUSTOMER_SERVICE_URL` at the port that is actually open.

Timeouts, HTTP methods, paths, query names, body field names and the idempotency header are in `src/main/resources/application.yaml` under `ticketflow.services.<service>`. Each operation has `contract-confirmed`. While that flag is `false`, the tool is still discoverable, and invoking it returns `CONTRACT_NOT_CONFIRMED` without an HTTP call. Set the flag to `true` only after the owning team publishes the OpenAPI contract and the path, method and field names in this file match that contract.

| Setting | Meaning |
|---|---|
| `ticketflow.services.<service>.connect-timeout` | TCP connect timeout. Default `2s`. |
| `ticketflow.services.<service>.response-timeout` | Time to wait for the response. Default `5s`. |
| `ticketflow.services.<service>.idempotency-header` | Header used when a tool receives `idempotencyKey`. Default `Idempotency-Key`. Whether any service honors it is not confirmed. |
| `ticketflow.security.propagate-authorization` | Copy the inbound `Authorization` header onto the downstream call. Default `true`. |
| `ticketflow.mcp.allowed-hosts` | Accepted `Host` headers. `localhost:*` matches any port. |
| `ticketflow.mcp.allowed-origins` | Accepted browser `Origin` headers. Absent `Origin` is allowed. |

An `Authorization` value is forwarded and is never written to logs or tool results. This server does not validate the token. Keycloak is a later roadmap step and is not part of the platform yet, so there is no confirmed way to derive the customer id from a token. Tools that need a customer ask for `customerId`.

The MCP endpoint itself has Host and Origin checks and no authentication layer. Keep it off the public internet until the platform security design exists.

## Registered tools

Consequential tools (`create_booking`, `cancel_booking`, `initiate_payment`, `resend_booking_confirmation`) require `userConfirmed: true`. The assistant must set that flag only after the user has explicitly agreed. `false` or a missing confirmation returns `CONFIRMATION_REQUIRED` with `isError: true`, and no downstream call is made.

`initiate_payment` accepts a booking id. It rejects card numbers, CVV, PAN and an amount. Payment Service decides the amount from the booking. A tool result of `OK` means the HTTP call returned a JSON body. It does not mean the payment was captured. Read `data` for the status Payment Service returned.

Mutating calls are not retried. When the caller supplies `idempotencyKey`, it is sent on the configured header so the downstream service can deduplicate if it supports that header.

Identifier fields are limited to letters, digits, `_` and `-`. `customerId` must be the positive numeric id Customer Service already uses. `quantity` is limited to 1..50 at this boundary; Booking Service still owns the real inventory limit.

| Tool | Required arguments | Optional arguments | Downstream contract |
|---|---|---|---|
| `search_events` | | `keyword`, `category`, `date` (`YYYY-MM-DD`), `location` | Not confirmed |
| `get_event_details` | `eventId` | | Not confirmed |
| `get_upcoming_events` | | `limit` (1..100), `category`, `location` | Not confirmed |
| `check_ticket_availability` | `eventId` | `ticketType`, `quantity` | Not confirmed |
| `get_my_tickets` | `customerId` | | Not confirmed |
| `create_booking` | `eventId`, `customerId`, `quantity`, `userConfirmed` | `ticketType`, `idempotencyKey` | Not confirmed |
| `get_booking_status` | `bookingId` | | Not confirmed |
| `cancel_booking` | `bookingId`, `customerId`, `userConfirmed` | `reason`, `idempotencyKey` | Not confirmed |
| `initiate_payment` | `bookingId`, `userConfirmed` | `idempotencyKey` | Not confirmed |
| `get_payment_status` | `paymentId` | | Not confirmed |
| `get_customer_profile` | `customerId` | | Confirmed: `GET /api/customers/{customerId}` |
| `get_notification_history` | `customerId` | `bookingId` | Not confirmed |
| `resend_booking_confirmation` | `bookingId`, `customerId`, `userConfirmed` | `idempotencyKey` | Not confirmed |

Every input schema sets `additionalProperties` to `false`.

### Confirmed Customer Service call

`get_customer_profile` calls the endpoint implemented in `customer-service`:

```http
GET /api/customers/{id}
```

The JSON body uses `id`, `firstName`, `lastName`, `email`, `phone` and `createdAt`. Errors from that service are RFC 9457 problem details. This tool returns those same profile fields. It does not add credentials or payment data.

### Example: profile

Arguments:

```json
{ "customerId": "42" }
```

Success result (`isError` false). `data` is the Customer Service body:

```json
{
  "status": "OK",
  "service": "Customer Service",
  "operation": "get_customer_profile",
  "data": {
    "id": 42,
    "firstName": "Sara",
    "lastName": "Idrissi",
    "email": "sara@example.com",
    "phone": "+212600000000",
    "createdAt": "2026-01-01T00:00:00Z"
  }
}
```

### Example: booking without confirmation

Arguments:

```json
{
  "eventId": "evt-1",
  "customerId": "42",
  "quantity": 2,
  "userConfirmed": false
}
```

Result (`isError` true). Booking Service is not called:

```json
{
  "status": "ERROR",
  "code": "CONFIRMATION_REQUIRED",
  "operation": "create_booking",
  "message": "The operation was not executed. Ask the user to confirm create_booking explicitly, then call the tool again with userConfirmed set to true."
}
```

### Example: unconfirmed catalog

```json
{ "keyword": "jazz" }
```

`search_events` answers with `code` `CONTRACT_NOT_CONFIRMED` until `ticketflow.services.event.operations.search.contract-confirmed` is true and `EVENT_SERVICE_URL` points at the real service.

## Error codes

| Code | When |
|---|---|
| `INVALID_ARGUMENTS` | A value fails a local check such as date, id shape or quantity. |
| `CONFIRMATION_REQUIRED` | A consequential tool was called without `userConfirmed: true`. |
| `CONTRACT_NOT_CONFIRMED` | The operation's OpenAPI contract is still unpublished. No HTTP call. |
| `SERVICE_NOT_CONFIGURED` | The contract flag is true and the base URL is missing. |
| `AUTHENTICATION_FAILED` | The downstream service returned HTTP 401 or 403. The body is discarded. |
| `DOWNSTREAM_TIMEOUT` | The response timeout elapsed. |
| `DOWNSTREAM_UNAVAILABLE` | The connection failed. |
| `DOWNSTREAM_HTTP_ERROR` | Any other HTTP 4xx or 5xx. A problem-detail `detail` or `title` is included when the body is JSON. |
| `UNEXPECTED_RESPONSE` | The body was not a JSON object or array, or it was larger than 256 KiB. |
| `MISCONFIGURED` | The operation path or method in configuration cannot be used. |
| `UNEXPECTED_ERROR` | An unexpected failure. The message has no stack trace. |

Failed calls set MCP `isError` to true. They are not rewritten as `status: OK`.

Response objects are scanned for payment-credential field names (`cardNumber`, `cvv`, `cvc`, `pan`, tokens, passwords, secrets). Matching values are replaced with `[redacted]` before they reach the assistant. Profile fields returned by Customer Service, including email and phone, are left intact because that is the published profile contract.

## Connecting the AI Assistant

The AI Assistant Service is not in this repository yet. When it exists, point its MCP client at:

```text
http://mcp-tool-server:8090/mcp
```

Use Streamable HTTP. The server name is `ticketflow-mcp` and the instructions on the server describe the confirmation gate and the unconfirmed contracts.

The assistant should:

1. Call `tools/list` and treat tool descriptions as the source of which calls change data.
2. Ask the user before `create_booking`, `cancel_booking`, `initiate_payment` and `resend_booking_confirmation`, then retry with `userConfirmed: true`.
3. Show `CONFIRMATION_REQUIRED`, `CONTRACT_NOT_CONFIRMED` and downstream errors to the user instead of assuming the action happened.
4. Read payment status from `get_payment_status` after `initiate_payment`. Do not treat the initiate call as a captured payment.
5. Send the caller's `Authorization` header on the MCP request when a token exists. This server forwards it and does not inspect it.

## What still needs a service team

These items are intentionally not invented:

| Topic | State |
|---|---|
| Event, Ticket, Booking, Payment and Notification Service URLs, ports, paths, methods, query names and body fields | Placeholder values in `application.yaml`, `contract-confirmed: false` |
| Idempotency-Key support | Header is forwarded when a key is present. No service has confirmed that it stores the key. |
| Authentication scheme, token audience and how a token maps to `customerId` | Not implemented. Customer Service currently has no authentication. |
| Who may cancel a booking or pay for it | Must be enforced by Booking Service and Payment Service. This server also requires `customerId` on cancel and the profile id on customer reads. |
| Payment statuses, redirect URLs, and the capture flow | Not confirmed. No card data is accepted here. |
| Ticket types, seat maps and inventory limits | Not confirmed. The quantity cap of 50 is only a guardrail on model input. |
| Notification channels and resend semantics | Not confirmed. |
| Error body shape for services other than Customer Service | Problem-detail `detail` and `title` are read when present. Other bodies become a generic HTTP status message. |
| API keys or service-to-service credentials | Not configured. Only the inbound `Authorization` header is propagated. |
| Eureka and Config Server registration for this module | Not wired. Those platform modules are still marked planned, and this service starts without them. |

When a contract arrives, update that operation's `method`, `path`, `query` and `body` map, set the base URL, then set `contract-confirmed: true`. Flat JSON fields can be renamed from configuration. A nested body or a header other than `Authorization` and the idempotency header needs a small change in the matching client.

## Tests

`DownstreamHttpTest` uses WireMock for a successful customer read, authorization forwarding, HTTP 401, HTTP 409, payment timeout, and credential redaction.

`ConsequentialOperationTest` checks that booking and payment methods do not call their client without confirmation, and that payment initiation forwards only `bookingId`.

`McpServerStartupTest` starts the server, lists the thirteen tools over Streamable HTTP, and checks the confirmation and contract gates.

`McpDownstreamIntegrationTest` drives the same endpoint against WireMock for a customer profile read, a refused payment, an authentication failure, a payment timeout, and a booking that is not sent when confirmation is missing.
