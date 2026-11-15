package com.ticketflow.mcptoolserver.clients;

import com.ticketflow.mcptoolserver.config.DownstreamServicesProperties;
import com.ticketflow.mcptoolserver.config.DownstreamServicesProperties.Operation;
import com.ticketflow.mcptoolserver.config.DownstreamServicesProperties.Service;
import com.ticketflow.mcptoolserver.config.SecurityProperties;
import com.ticketflow.mcptoolserver.exceptions.ToolFailure;
import com.ticketflow.mcptoolserver.exceptions.ToolFailureException;
import com.ticketflow.mcptoolserver.security.CallerContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

@Component
public class DownstreamHttp implements ServiceInvoker {

    private static final Logger log = LoggerFactory.getLogger(DownstreamHttp.class);
    private static final int MAX_RESPONSE_CHARS = 256 * 1024;
    private static final int MAX_ERROR_BODY_CHARS = 2_048;
    private static final Set<String> NEVER_FORWARD = Set.of("userconfirmed", "authorization", "idempotencykey");
    private static final TypeRef<Object> OBJECT = new TypeRef<>() {
    };
    private static final TypeRef<Map<String, Object>> MAP = new TypeRef<>() {
    };

    private final DownstreamServicesProperties services;
    private final SecurityProperties security;
    private final McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();
    private final Map<String, RestClient> clients = new ConcurrentHashMap<>();

    public DownstreamHttp(DownstreamServicesProperties services, SecurityProperties security) {
        this.services = services;
        this.security = security;
    }

    @Override
    public Object invoke(
            String serviceKey,
            String operationName,
            Map<String, String> pathVariables,
            Map<String, Object> arguments,
            CallerContext caller,
            String idempotencyKey) {
        Service service = services.service(serviceKey);
        String serviceName = displayName(serviceKey);
        Operation operation = service.getOperations().get(operationName);
        if (operation == null || operation.getPath() == null || operation.getPath().isBlank()) {
            throw new ToolFailureException(ToolFailure.misconfigured(
                    serviceName,
                    operationName,
                    serviceName + " operation " + operationName + " is not configured."));
        }
        if (!operation.isContractConfirmed()) {
            throw new ToolFailureException(ToolFailure.contractNotConfirmed(serviceName, operationName));
        }

        String baseUrl = normalizeBaseUrl(service.getBaseUrl());
        if (baseUrl == null) {
            throw new ToolFailureException(ToolFailure.serviceNotConfigured(serviceName, operationName));
        }

        HttpMethod method = parseMethod(serviceName, operationName, operation.getMethod());
        String path = expandPath(serviceName, operationName, operation.getPath(), pathVariables);
        Map<String, Object> body = method == HttpMethod.GET ? Map.of() : bodyOf(operation, arguments);
        CallerContext safeCaller = caller == null ? CallerContext.anonymous() : caller;

        try {
            RestClient.RequestBodySpec request = client(serviceKey, service, baseUrl)
                    .method(method)
                    .uri(uriBuilder -> {
                        uriBuilder.path(path);
                        if (operation.getQuery() != null && arguments != null) {
                            operation.getQuery().forEach((argumentName, parameterName) -> {
                                Object value = arguments.get(argumentName);
                                if (value != null && !value.toString().isBlank()) {
                                    uriBuilder.queryParam(parameterName, value.toString());
                                }
                            });
                        }
                        return uriBuilder.build();
                    })
                    .headers(headers -> applyHeaders(headers, service, method, safeCaller, idempotencyKey, serviceName, operationName));

            RestClient.RequestHeadersSpec<?> outgoing = request;
            if (!body.isEmpty()) {
                outgoing = request.contentType(MediaType.APPLICATION_JSON).body(writeJson(body));
            }

            String raw = outgoing.retrieve()
                    .onStatus(status -> status.isError(), (httpRequest, response) -> {
                        throw httpFailure(serviceName, operationName, response);
                    })
                    .body(String.class);
            log.info("Downstream call completed: service={} operation={} method={}", serviceName, operationName, method);
            return parseSuccess(serviceName, operationName, raw);
        } catch (ToolFailureException ex) {
            log.warn("Downstream call rejected: service={} operation={} code={}",
                    serviceName, operationName, ex.failure().code());
            throw ex;
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                log.warn("Downstream timeout: service={} operation={}", serviceName, operationName);
                throw new ToolFailureException(ToolFailure.timeout(serviceName, operationName));
            }
            log.warn("Downstream unavailable: service={} operation={} cause={}",
                    serviceName, operationName, rootCause(ex).getClass().getSimpleName());
            throw new ToolFailureException(ToolFailure.unavailable(serviceName, operationName));
        }
    }

    private RestClient client(String serviceKey, Service service, String baseUrl) {
        return clients.computeIfAbsent(serviceKey, ignored -> {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(service.getConnectTimeout())
                    .build();
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(service.getResponseTimeout());
            return RestClient.builder()
                    .baseUrl(baseUrl)
                    .requestFactory(requestFactory)
                    .build();
        });
    }

    private void applyHeaders(
            org.springframework.http.HttpHeaders headers,
            Service service,
            HttpMethod method,
            CallerContext caller,
            String idempotencyKey,
            String serviceName,
            String operationName) {
        if (security.isPropagateAuthorization() && caller.hasAuthorization()) {
            String authorization = caller.authorizationHeader();
            if (authorization.length() > 4096 || authorization.indexOf('\r') >= 0 || authorization.indexOf('\n') >= 0) {
                throw new ToolFailureException(ToolFailure.authenticationFailed(serviceName, operationName, 400));
            }
            headers.set(org.springframework.http.HttpHeaders.AUTHORIZATION, authorization);
        }
        if (idempotencyKey != null
                && !idempotencyKey.isBlank()
                && method != HttpMethod.GET
                && service.getIdempotencyHeader() != null
                && !service.getIdempotencyHeader().isBlank()) {
            headers.set(service.getIdempotencyHeader(), idempotencyKey);
        }
    }

    private Object parseSuccess(String serviceName, String operationName, String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        if (raw.length() > MAX_RESPONSE_CHARS) {
            throw new ToolFailureException(ToolFailure.unexpectedResponse(
                    serviceName,
                    operationName,
                    serviceName + " returned a response larger than 256 KiB. It was not forwarded."));
        }
        String trimmed = raw.stripLeading();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            throw new ToolFailureException(ToolFailure.unexpectedResponse(
                    serviceName,
                    operationName,
                    serviceName + " returned a response that is not a JSON object or array."));
        }
        try {
            Object parsed = jsonMapper.readValue(raw, OBJECT);
            if (!(parsed instanceof Map<?, ?>) && !(parsed instanceof java.util.List<?>)) {
                throw new ToolFailureException(ToolFailure.unexpectedResponse(
                        serviceName,
                        operationName,
                        serviceName + " returned JSON that is not an object or array."));
            }
            return ResponseSanitizer.sanitize(parsed);
        } catch (IOException ex) {
            throw new ToolFailureException(ToolFailure.unexpectedResponse(
                    serviceName,
                    operationName,
                    serviceName + " returned JSON that could not be read."));
        }
    }

    private ToolFailureException httpFailure(String serviceName, String operationName, ClientHttpResponse response)
            throws IOException {
        int status = response.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new ToolFailureException(ToolFailure.authenticationFailed(serviceName, operationName, status));
        }
        String detail = problemMessage(serviceName, status, readErrorBody(response));
        return new ToolFailureException(ToolFailure.httpError(serviceName, operationName, status, detail));
    }

    private String problemMessage(String serviceName, int status, String raw) {
        if (raw == null || raw.isBlank()) {
            return serviceName + " returned HTTP " + status + ".";
        }
        try {
            Map<String, Object> body = jsonMapper.readValue(raw, MAP);
            Object detail = body.get("detail");
            if (detail instanceof String text && !text.isBlank()) {
                return serviceName + " returned HTTP " + status + ": " + truncate(text);
            }
            Object title = body.get("title");
            if (title instanceof String text && !text.isBlank()) {
                return serviceName + " returned HTTP " + status + ": " + truncate(text);
            }
            String serviceMessage = textValue(body.get("message"));
            String serviceCode = textValue(body.get("code"));
            if (serviceMessage != null && serviceCode != null) {
                return serviceName + " returned HTTP " + status + ": " + truncate(serviceCode + ": " + serviceMessage);
            }
            if (serviceMessage != null) {
                return serviceName + " returned HTTP " + status + ": " + truncate(serviceMessage);
            }
            if (serviceCode != null) {
                return serviceName + " returned HTTP " + status + ": " + truncate(serviceCode);
            }
        } catch (IOException ignored) {
            return serviceName + " returned HTTP " + status + ".";
        }
        return serviceName + " returned HTTP " + status + ".";
    }

    private static String readErrorBody(ClientHttpResponse response) {
        try (InputStream body = response.getBody()) {
            byte[] bytes = body.readNBytes(MAX_ERROR_BODY_CHARS);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    private String writeJson(Map<String, Object> body) {
        try {
            return jsonMapper.writeValueAsString(body);
        } catch (IOException ex) {
            throw new ToolFailureException(ToolFailure.unexpected("request"));
        }
    }

    private static Map<String, Object> bodyOf(Operation operation, Map<String, Object> arguments) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (operation.getBody() == null || arguments == null) {
            return body;
        }
        operation.getBody().forEach((argumentName, fieldName) -> {
            if (NEVER_FORWARD.contains(argumentName.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT))) {
                return;
            }
            Object value = arguments.get(argumentName);
            if (value != null) {
                body.put(fieldName, value);
            }
        });
        return body;
    }

    private static String expandPath(
            String serviceName,
            String operationName,
            String template,
            Map<String, String> pathVariables) {
        String path = template.startsWith("/") ? template : "/" + template;
        if (pathVariables != null) {
            for (Map.Entry<String, String> entry : pathVariables.entrySet()) {
                path = path.replace("{" + entry.getKey() + "}", encodeSegment(serviceName, operationName, entry.getValue()));
            }
        }
        if (path.contains("{") || path.contains("}")) {
            throw new ToolFailureException(ToolFailure.misconfigured(
                    serviceName,
                    operationName,
                    serviceName + " path for " + operationName + " still contains an unresolved placeholder."));
        }
        return path;
    }

    private static String encodeSegment(String serviceName, String operationName, String value) {
        if (value == null
                || value.isBlank()
                || value.contains("/")
                || value.contains("\\")
                || value.contains("..")) {
            throw new ToolFailureException(ToolFailure.invalid(
                    "A path parameter for " + serviceName + " is missing or unsafe."));
        }
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private static HttpMethod parseMethod(String serviceName, String operationName, String method) {
        try {
            return HttpMethod.valueOf(method.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ToolFailureException(ToolFailure.misconfigured(
                    serviceName,
                    operationName,
                    serviceName + " operation " + operationName + " has no supported HTTP method."));
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return null;
        }
        return trimmed;
    }

    private static boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HttpTimeoutException || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            if (current instanceof java.net.http.HttpConnectTimeoutException) {
                return true;
            }
            String name = current.getClass().getSimpleName();
            if (name.contains("Timeout")) {
                return true;
            }
            if (current instanceof ConnectException) {
                return false;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String textValue(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return null;
    }

    private static String truncate(String value) {
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= 300 ? compact : compact.substring(0, 300);
    }

    static String displayName(String serviceKey) {
        return switch (serviceKey) {
            case "event" -> "Event Service";
            case "ticket" -> "Ticket Service";
            case "booking" -> "Booking Service";
            case "payment" -> "Payment Service";
            case "customer" -> "Customer Service";
            default -> serviceKey;
        };
    }
}
