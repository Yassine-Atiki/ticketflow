package com.ticketflow.mcptoolserver.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ticketflow.services")
public class DownstreamServicesProperties {

    private Service event = new Service();
    private Service ticket = new Service();
    private Service booking = new Service();
    private Service payment = new Service();
    private Service customer = new Service();

    public Service service(String key) {
        return switch (key) {
            case "event" -> event;
            case "ticket" -> ticket;
            case "booking" -> booking;
            case "payment" -> payment;
            case "customer" -> customer;
            default -> throw new IllegalArgumentException("Unknown downstream service: " + key);
        };
    }

    public Service getEvent() {
        return event;
    }

    public void setEvent(Service event) {
        this.event = event;
    }

    public Service getTicket() {
        return ticket;
    }

    public void setTicket(Service ticket) {
        this.ticket = ticket;
    }

    public Service getBooking() {
        return booking;
    }

    public void setBooking(Service booking) {
        this.booking = booking;
    }

    public Service getPayment() {
        return payment;
    }

    public void setPayment(Service payment) {
        this.payment = payment;
    }

    public Service getCustomer() {
        return customer;
    }

    public void setCustomer(Service customer) {
        this.customer = customer;
    }

    public static final class Service {

        private String baseUrl = "";
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration responseTimeout = Duration.ofSeconds(5);
        private String idempotencyHeader = "Idempotency-Key";
        private Map<String, Operation> operations = new LinkedHashMap<>();

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getResponseTimeout() {
            return responseTimeout;
        }

        public void setResponseTimeout(Duration responseTimeout) {
            this.responseTimeout = responseTimeout;
        }

        public String getIdempotencyHeader() {
            return idempotencyHeader;
        }

        public void setIdempotencyHeader(String idempotencyHeader) {
            this.idempotencyHeader = idempotencyHeader;
        }

        public Map<String, Operation> getOperations() {
            return operations;
        }

        public void setOperations(Map<String, Operation> operations) {
            this.operations = operations;
        }
    }

    public static final class Operation {

        private String method = "GET";
        private String path = "";
        private boolean contractConfirmed;
        private Map<String, String> query = new LinkedHashMap<>();
        private Map<String, String> body = new LinkedHashMap<>();

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public boolean isContractConfirmed() {
            return contractConfirmed;
        }

        public void setContractConfirmed(boolean contractConfirmed) {
            this.contractConfirmed = contractConfirmed;
        }

        public Map<String, String> getQuery() {
            return query;
        }

        public void setQuery(Map<String, String> query) {
            this.query = query;
        }

        public Map<String, String> getBody() {
            return body;
        }

        public void setBody(Map<String, String> body) {
            this.body = body;
        }
    }
}
