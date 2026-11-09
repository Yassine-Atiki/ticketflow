package com.ticketflow.customerservice.service;

import com.ticketflow.customerservice.dto.PreferencesRequest;
import com.ticketflow.customerservice.dto.PreferencesResponse;
import com.ticketflow.customerservice.entities.CustomerPreferences;
import com.ticketflow.customerservice.repos.CustomerPreferencesRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PreferencesService {

    private final CustomerService customers;
    private final CustomerPreferencesRepo repo;

    public PreferencesService(CustomerService customers, CustomerPreferencesRepo repo) {
        this.customers = customers;
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public PreferencesResponse get(Long customerId) {
        customers.get(customerId); // 404 if the customer does not exist
        return repo.findByCustomerId(customerId)
                .map(PreferencesResponse::from)
                .orElseGet(() -> PreferencesResponse.defaults(customerId));
    }

    public PreferencesResponse put(Long customerId, PreferencesRequest request) {
        customers.get(customerId); // 404 if the customer does not exist
        CustomerPreferences prefs = repo.findByCustomerId(customerId)
                .orElseGet(() -> new CustomerPreferences(customerId, request.language(),
                        request.notificationChannel(), request.marketingOptIn()));
        prefs.update(request.language(), request.notificationChannel(), request.marketingOptIn());
        return PreferencesResponse.from(repo.save(prefs));
    }
}