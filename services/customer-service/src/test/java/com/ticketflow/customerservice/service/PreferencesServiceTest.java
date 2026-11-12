package com.ticketflow.customerservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ticketflow.customerservice.dto.PreferencesRequest;
import com.ticketflow.customerservice.dto.PreferencesResponse;
import com.ticketflow.customerservice.entities.Customer;
import com.ticketflow.customerservice.entities.CustomerPreferences;
import com.ticketflow.customerservice.entities.NotificationChannel;
import com.ticketflow.customerservice.exceptions.CustomerNotFoundException;
import com.ticketflow.customerservice.repos.CustomerPreferencesRepo;
import com.ticketflow.customerservice.repos.CustomerRepo;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreferencesServiceTest {

    @Mock CustomerService customers;
    @Mock CustomerRepo customerRepo;
    @Mock CustomerPreferencesRepo repo;
    @InjectMocks PreferencesService service;

    @Test
    void returnsDefaultsWhenNothingSaved() {
        when(repo.findByCustomerId(1L)).thenReturn(Optional.empty());

        PreferencesResponse r = service.get(1L);

        assertThat(r.language()).isEqualTo("fr");
        assertThat(r.notificationChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(r.marketingOptIn()).isFalse();
    }

    @Test
    void putCreatesThenReturnsSavedValues() {
        when(customerRepo.findByIdForUpdate(1L))
                .thenReturn(Optional.of(new Customer("A", "B", "a@b.c", null)));
        when(repo.findByCustomerId(1L)).thenReturn(Optional.empty());
        when(repo.save(any(CustomerPreferences.class))).thenAnswer(i -> i.getArgument(0));

        PreferencesResponse r = service.put(1L,
                new PreferencesRequest("ar", NotificationChannel.TELEGRAM, true));

        assertThat(r.language()).isEqualTo("ar");
        assertThat(r.notificationChannel()).isEqualTo(NotificationChannel.TELEGRAM);
        assertThat(r.marketingOptIn()).isTrue();
    }

    @Test
    void getRejectsUnknownCustomer() {
        when(customers.get(99L)).thenThrow(new CustomerNotFoundException(99L));

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void putRejectsUnknownCustomer() {
        when(customerRepo.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.put(99L,
                new PreferencesRequest("fr", NotificationChannel.EMAIL, false)))
                .isInstanceOf(CustomerNotFoundException.class);
    }
}