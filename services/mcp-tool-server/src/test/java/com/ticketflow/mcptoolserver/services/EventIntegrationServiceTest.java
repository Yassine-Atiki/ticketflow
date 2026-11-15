package com.ticketflow.mcptoolserver.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow.mcptoolserver.clients.EventServiceClient;
import com.ticketflow.mcptoolserver.clients.ServiceInvoker;
import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.tools.ToolArguments;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventIntegrationServiceTest {

    @Test
    void searchForwardsThePublishedSortParameter() {
        CapturingInvoker invoker = new CapturingInvoker();
        EventIntegrationService events = new EventIntegrationService(new EventServiceClient(invoker));

        events.search(new ToolArguments(Map.of("q", "jazz", "sort", "startDate,asc")), CallerContext.anonymous());

        assertThat(invoker.operation).isEqualTo("search");
        assertThat(invoker.arguments).containsEntry("q", "jazz").containsEntry("sort", "startDate,asc");
    }

    private static final class CapturingInvoker implements ServiceInvoker {

        private String operation;
        private Map<String, Object> arguments;

        @Override
        public Object invoke(
                String serviceKey,
                String operationName,
                Map<String, String> pathVariables,
                Map<String, Object> arguments,
                CallerContext caller,
                String idempotencyKey) {
            this.operation = operationName;
            this.arguments = arguments;
            return Map.of();
        }
    }
}
