package com.ticketflow.customerservice.dto;

import com.ticketflow.customerservice.entities.NotificationChannel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PreferencesRequest(
        @NotNull @Pattern(regexp = "fr|en|ar") String language,
        @NotNull NotificationChannel notificationChannel,
        boolean marketingOptIn) {
}