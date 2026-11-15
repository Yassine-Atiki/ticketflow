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

Customer Service is the only downstream API this module calls. Its README documents port 8081. Its `application.properties` does not set `server.port`, so a process started without Config Server listens on 8080. Point `CUSTOMER_SERVICE_URL` at the port that is actually open.

Event, ticket, booking and payment paths in `application.yaml` match the contracts published on the team branches. Those services do not implement the operations yet, so `contract-confirmed` stays `false`. There is no notification service, and this module does not expose notification tools.

Timeouts, HTTP methods, paths, query names, body field names and the idempotency header are in `src/main/resources/application.yaml` under `ticketflow.services.<service>`. Each operation has `contract-confirmed`. While that flag is `false`, the tool is still discoverable, and invoking it returns `CONTRACT_NOT_CONFIRMED` without an HTTP call. Set the flag to `true` only after the owning team publishes the OpenAPI contract and the path, method and field names in this file match that contract.

| Setting | Meaning |
|---|---|
| `ticketflow.services.<service>.connect-timeout` | TCP connect timeout. Default `2s`. |
| `ticketflow.services.<service>.response-timeout` | Time to wait for the response. Default `5s`. |
| `ticketflow.services.<service>.idempotency-header` | Header sent for `idempotencyKey`. Default `Idempotency-Key`. Booking create and payment initiation require it. |
| `ticketflow.security.propagate-authorization` | Copy the inbound `Authorization` header onto the downstream call. Default `true`. |
| `ticketflow.mcp.allowed-hosts` | Accepted `Host` headers. `localhost:*` matches any port. |
| `ticketflow.mcp.allowed-origins` | Accepted browser `Origin` headers. Absent `Origin` is allowed. |

An `Authorization` value is forwarded and is never written to logs or tool results. This server does not validate the token. Booking and ticket tools require `Authorization: Bearer ...` before they would call downstream, because those contracts take the caller from the token and reject a caller-supplied customer id. Customer profile still takes the numeric `customerId` used by `GET /api/customers/{id}`.

The MCP endpoint itself has Host and Origin checks and no authentication layer. Keep it off the public internet until the platform security design exists.

## Registered tools

Consequential tools (`create_booking`, `cancel_booking`, `initiate_payment`, `update_customer_preferences`) require `userConfirmed: true`. The assistant must set that flag only after the user has explicitly agreed. `false` or a missing confirmation returns `CONFIRMATION_REQUIRED` with `isError: true`, and no downstream call is made.

`initiate_payment` accepts a booking id. It rejects card numbers, CVV, PAN and an amount. Payment Service decides the amount from the booking. A tool result of `OK` means the HTTP call returned a JSON body. It does not mean the payment was captured. Read `data` for the status Payment Service returned.

Mutating calls are not retried. `create_booking` and `initiate_payment` require `idempotencyKey` and send it as `Idempotency-Key`. Booking accepts at most 100 characters. Payment accepts at most 255.

Booking `eventId`, `ticketTierId` and `bookingId`, and payment `bookingId` and `paymentId`, must be UUIDs. `customerId` on the profile tool must be the positive numeric id Customer Service already uses. A booking line quantity is 1..10, which is the cap in the booking contract. Event details keep the identifier accepted by `GET /api/v1/events/{id}`.

| Tool | Required arguments | Optional arguments | Downstream |
|---|---|---|---|
| `search_events` | | `q`, `categoryId`, `city`, `dateFrom`, `dateTo`, `page`, `size`, `sort` | Disabled. Contract: `GET /api/v1/events` |
| `get_event_details` | `eventId` | | Disabled. Contract: `GET /api/v1/events/{id}` |
| `get_my_tickets` | bearer token | `page`, `size`, `status` | Disabled. Contract: `GET /api/v1/me/tickets` |
| `get_my_ticket` | `ticketId`, bearer token | | Disabled. Contract: `GET /api/v1/me/tickets/{ticketId}` (`controlCode` redacted) |
| `create_booking` | `eventId`, `lines`, `userConfirmed`, `idempotencyKey`, bearer token | | Disabled. Contract: `POST /api/v1/bookings` |
| `list_my_bookings` | bearer token | `status`, `page`, `size` | Disabled. Contract: `GET /api/v1/me/bookings` |
| `get_booking_status` | `bookingId`, bearer token | | Disabled. Contract: `GET /api/v1/bookings/{id}` |
| `cancel_booking` | `bookingId`, `userConfirmed`, bearer token | `reason`, `idempotencyKey` | Disabled. Contract: `POST /api/v1/bookings/{id}/cancel` |
| `initiate_payment` | `bookingId`, `userConfirmed`, `idempotencyKey` | `provider` | Disabled. Contract: `POST /api/payments` |
| `get_payment_status` | `paymentId` | | Disabled. Contract: `GET /api/payments/{id}` |
| `get_payments_by_booking` | `bookingId` | | Disabled. Contract: `GET /api/payments/booking/{bookingId}` |
| `get_customer_profile` | `customerId` | | Enabled: `GET /api/customers/{customerId}` |
| `get_customer_preferences` | `customerId` | | Disabled until Customer #7. Contract: `GET /api/customers/{id}/preferences` |
| `update_customer_preferences` | `customerId`, `language`, `notificationChannel`, `userConfirmed` | `marketingOptIn` | Disabled until Customer #7. Contract: `PUT /api/customers/{id}/preferences` |

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
  "eventId": "e4f5a6b7-c8d9-4e0f-a1b2-c3d4e5f6a7b8",
  "lines": [{ "ticketTierId": "f1e2d3c4-b5a6-4978-8695-a4b3c2d1e0f9", "quantity": 2 }],
  "idempotencyKey": "idem-1",
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
{ "q": "jazz" }
```

`search_events` answers with `code` `CONTRACT_NOT_CONFIRMED` until Event Service implements `GET /api/v1/events` and `contract-confirmed` is set to true.

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
| `DOWNSTREAM_HTTP_ERROR` | Any other HTTP 4xx or 5xx. A problem-detail `detail` or `title`, or a JSON `code` and `message`, is included when present. |
| `UNEXPECTED_RESPONSE` | The body was not a JSON object or array, or it was larger than 256 KiB. |
| `MISCONFIGURED` | The operation path or method in configuration cannot be used. |
| `UNEXPECTED_ERROR` | An unexpected failure. The message has no stack trace. |

Failed calls set MCP `isError` to true. They are not rewritten as `status: OK`.

Response objects are scanned for payment-credential field names (`cardNumber`, `cvv`, `cvc`, `pan`, tokens, passwords, secrets) and ticket presentation secrets (`controlCode`). Matching values are replaced with `[redacted]` before they reach the assistant. Profile fields returned by Customer Service, including email and phone, are left intact because that is the published profile contract.

## Connecting the AI Assistant

Point the AI Assistant MCP client at:

```text
http://mcp-tool-server:8090/mcp
```

Use Streamable HTTP. The server name is `ticketflow-mcp` and the instructions on the server describe the confirmation gate and the unconfirmed contracts.

The assistant should:

1. Call `tools/list` and treat tool descriptions as the source of which calls change data.
2. Ask the user before `create_booking`, `cancel_booking`, `initiate_payment` and `update_customer_preferences`, then retry with `userConfirmed: true`.
3. Show `CONFIRMATION_REQUIRED`, `CONTRACT_NOT_CONFIRMED` and downstream errors to the user instead of assuming the action happened.
4. Read payment status from `get_payment_status` after `initiate_payment`. Do not treat the initiate call as a captured payment.
5. Send the caller's `Authorization` header on the MCP request when a token exists. This server forwards it and does not inspect it.

## Coming next

| When | MCP action |
|---|---|
| Customer #7 merges | Set preferences ops `contract-confirmed: true`; run against real Customer Service |
| Event controllers land | Enable `search_events` / `get_event_details`; tighten `eventId` to integer if Event keeps int64 |
| Booking controllers land | Enable create/status/cancel/`list_my_bookings` |
| Ticket controllers land | Enable `get_my_tickets` / `get_my_ticket` |
| Payment controllers land | Enable initiate/status/`get_payments_by_booking` |
| Team ID decision | Align Event vs Booking UUID/Long across tools |
| Keycloak | Validate JWT; stop requiring caller-supplied `customerId` where the token subject should own the resource |
| Compose | Add event/booking/ticket/payment services; point MCP env URLs at them |
| AI Assistant | Point its MCP client at `http://mcp-tool-server:8090/mcp` |

Still out of scope unless product asks: notification tools, payment simulate/webhooks, ticket verify, event admin CRUD.

## What still needs a service team

These items are intentionally not invented:

| Topic | State |
|---|---|
| Event, ticket, booking and payment controllers | Contracts are mapped and `contract-confirmed` is false until the controllers exist |
| Customer preferences | Mapped with `contract-confirmed: false` until Customer PR #7 lands |
| Notification tools | Removed. No notification service or contract exists |
| JWT signature, issuer and audience | Not validated. Booking and ticket calls require a Bearer header and forward it |
| Payment capture | Not interpreted. No card data is accepted |
| Eureka registration | Client is on the classpath and disabled. Config Server import is optional so this process still starts without port 8888 |

When a contract arrives, update that operation's `method`, `path`, `query` and `body` map, set the base URL, then set `contract-confirmed: true`. Flat JSON fields can be renamed from configuration. A nested body or a header other than `Authorization` and the idempotency header needs a small change in the matching client.

## Tests

`DownstreamHttpTest` uses WireMock for a successful customer read, authorization forwarding, HTTP 401, HTTP 409, payment timeout, and credential redaction.

`ConsequentialOperationTest` checks that booking and payment methods do not call their client without confirmation, and that payment initiation forwards only `bookingId`.

`McpServerStartupTest` starts the server, lists the fourteen tools over Streamable HTTP, and checks the confirmation gate, the contract gate, and the Compose host name.

`PublishedContractAlignmentTest` checks the configured paths and bodies against the published team contracts and checks that unimplemented operations stay disabled.

`McpDownstreamIntegrationTest` drives the same endpoint against WireMock for a customer profile read, a refused payment, an authentication failure, a payment timeout, and a booking that is not sent when confirmation is missing.
