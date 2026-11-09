package com.ticketflow.customerservice.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_preferences")
public class CustomerPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long customerId;

    @Column(nullable = false)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel notificationChannel;

    @Column(nullable = false)
    private boolean marketingOptIn;

    protected CustomerPreferences() {
        // required by JPA
    }

    public CustomerPreferences(Long customerId, String language,
                               NotificationChannel notificationChannel, boolean marketingOptIn) {
        this.customerId = customerId;
        this.language = language;
        this.notificationChannel = notificationChannel;
        this.marketingOptIn = marketingOptIn;
    }

    public void update(String language, NotificationChannel notificationChannel, boolean marketingOptIn) {
        this.language = language;
        this.notificationChannel = notificationChannel;
        this.marketingOptIn = marketingOptIn;
    }

    public Long getCustomerId() { return customerId; }
    public String getLanguage() { return language; }
    public NotificationChannel getNotificationChannel() { return notificationChannel; }
    public boolean isMarketingOptIn() { return marketingOptIn; }
}