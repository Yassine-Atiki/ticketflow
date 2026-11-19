package com.ticketflow.customerservice.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "customer_preferences")
public class CustomerPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_customer_preferences_customer"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Customer customer;

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

    public CustomerPreferences(Customer customer, String language,
                               NotificationChannel notificationChannel, boolean marketingOptIn) {
        this.customer = customer;
        this.language = language;
        this.notificationChannel = notificationChannel;
        this.marketingOptIn = marketingOptIn;
    }

    public void update(String language, NotificationChannel notificationChannel, boolean marketingOptIn) {
        this.language = language;
        this.notificationChannel = notificationChannel;
        this.marketingOptIn = marketingOptIn;
    }

    public Long getCustomerId() { return customer.getId(); }
    public String getLanguage() { return language; }
    public NotificationChannel getNotificationChannel() { return notificationChannel; }
    public boolean isMarketingOptIn() { return marketingOptIn; }
}