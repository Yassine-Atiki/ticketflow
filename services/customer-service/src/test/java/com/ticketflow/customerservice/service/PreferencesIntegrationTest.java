package com.ticketflow.customerservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow.customerservice.dto.CustomerRequest;
import com.ticketflow.customerservice.dto.PreferencesRequest;
import com.ticketflow.customerservice.entities.Customer;
import com.ticketflow.customerservice.entities.NotificationChannel;
import com.ticketflow.customerservice.exceptions.CustomerNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class PreferencesIntegrationTest {

    @Autowired CustomerService customers;
    @Autowired PreferencesService preferences;
    @Autowired JdbcTemplate jdbc;

    private Customer newCustomer() {
        return customers.create(new CustomerRequest("Test", "User",
                UUID.randomUUID() + "@example.com", null));
    }

    private int countPreferences(Long customerId) {
        return jdbc.queryForObject(
                "select count(*) from customer_preferences where customer_id = ?",
                Integer.class, customerId);
    }

    @Test
    void deletingCustomerDeletesItsPreferences() {
        Customer c = newCustomer();
        preferences.put(c.getId(), new PreferencesRequest("ar", NotificationChannel.TELEGRAM, true));
        assertThat(countPreferences(c.getId())).isEqualTo(1);

        customers.delete(c.getId());

        assertThat(countPreferences(c.getId())).isZero();
    }

    @Test
    void concurrentFirstPutsCreateASingleRowWithoutError() throws Exception {
        Customer c = newCustomer();
        Long id = c.getId();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit((Callable<Object>) () -> {
                start.await();
                return preferences.put(id,
                        new PreferencesRequest("fr", NotificationChannel.EMAIL, false));
            }));
        }
        start.countDown();
        for (Future<Object> f : results) {
            f.get(20, TimeUnit.SECONDS); // rethrows any failure of a worker
        }
        pool.shutdown();

        assertThat(countPreferences(id)).isEqualTo(1);
    }

    @Test
    void concurrentDeleteAndPutNeverLeaveAnOrphan() throws Exception {
        for (int round = 0; round < 15; round++) {
            Long id = newCustomer().getId();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);

            Future<Void> put = pool.submit((Callable<Void>) () -> {
                start.await();
                try {
                    preferences.put(id, new PreferencesRequest("en", NotificationChannel.EMAIL, true));
                } catch (CustomerNotFoundException expectedWhenDeleteWins) {
                    // allowed outcome
                }
                return null;
            });
            Future<Void> delete = pool.submit((Callable<Void>) () -> {
                start.await();
                try {
                    customers.delete(id);
                } catch (CustomerNotFoundException ignored) {
                    // not expected, but harmless
                }
                return null;
            });
            start.countDown();
            put.get(20, TimeUnit.SECONDS);
            delete.get(20, TimeUnit.SECONDS);
            pool.shutdown();

            Integer orphans = jdbc.queryForObject(
                    "select count(*) from customer_preferences p "
                            + "where not exists (select 1 from customers c where c.id = p.customer_id)",
                    Integer.class);
            assertThat(orphans).isZero();
        }
    }
}