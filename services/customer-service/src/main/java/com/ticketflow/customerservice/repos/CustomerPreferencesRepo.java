package com.ticketflow.customerservice.repos;

import com.ticketflow.customerservice.entities.CustomerPreferences;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerPreferencesRepo extends JpaRepository<CustomerPreferences, Long> {

    @Query("select p from CustomerPreferences p where p.customer.id = :customerId")
    Optional<CustomerPreferences> findByCustomerId(@Param("customerId") Long customerId);
}