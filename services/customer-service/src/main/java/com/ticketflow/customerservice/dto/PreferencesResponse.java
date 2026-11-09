package com.ticketflow.customerservice.dto;

import com.ticketflow.customerservice.entities.CustomerPreferences;
import com.ticketflow.customerservice.entities.NotificationChannel;

public record PreferencesResponse(
        Long customerId,
        String language,
        NotificationChannel notificationChannel,
        boolean marketingOptIn) {

    public static PreferencesResponse from(CustomerPreferences p) {
        return new PreferencesResponse(p.getCustomerId(), p.getLanguage(),
                p.getNotificationChannel(), p.isMarketingOptIn());
    }

    public static PreferencesResponse defaults(Long customerId) {
        return new PreferencesResponse(customerId, "fr", NotificationChannel.EMAIL, false);
    }
}