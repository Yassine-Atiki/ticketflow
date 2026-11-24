package com.ticketflow.customerservice.service;

import com.ticketflow.customerservice.dto.PreferencesRequest;
import com.ticketflow.customerservice.dto.PreferencesResponse;
import com.ticketflow.customerservice.entities.Customer;
import com.ticketflow.customerservice.entities.CustomerPreferences;
import com.ticketflow.customerservice.exceptions.CustomerNotFoundException;
import com.ticketflow.customerservice.repos.CustomerPreferencesRepo;
import com.ticketflow.customerservice.repos.CustomerRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PreferencesService {

    private final CustomerService customers;
    private final CustomerRepo customerRepo;
    private final CustomerPreferencesRepo repo;

    public PreferencesService(CustomerService customers, CustomerRepo customerRepo,
                              CustomerPreferencesRepo repo) {
        this.customers = customers;
        this.customerRepo = customerRepo;
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
        // Row lock on the customer: serializes concurrent PUTs and any concurrent delete.
        Customer customer = customerRepo.findByIdForUpdate(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        CustomerPreferences prefs = repo.findByCustomerId(customerId)
                .orElseGet(() -> new CustomerPreferences(customer, request.language(),
                        request.notificationChannel(), request.marketingOptIn()));
        prefs.update(request.language(), request.notificationChannel(), request.marketingOptIn());
        return PreferencesResponse.from(repo.save(prefs));
    }
}