package com.ticketflow.customerservice.repos;

import com.ticketflow.customerservice.entities.CustomerPreferences;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerPreferencesRepo extends JpaRepository<CustomerPreferences, Long> {
    Optional<CustomerPreferences> findByCustomerId(Long customerId);
}