package com.ticketflow.mcptoolserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ticketflow.security")
public class SecurityProperties {

    /**
     * Forwards the inbound Authorization header to downstream services.
     * The header is not validated here: platform authentication is not implemented yet.
     */
    private boolean propagateAuthorization = true;

    public boolean isPropagateAuthorization() {
        return propagateAuthorization;
    }

    public void setPropagateAuthorization(boolean propagateAuthorization) {
        this.propagateAuthorization = propagateAuthorization;
    }
}
