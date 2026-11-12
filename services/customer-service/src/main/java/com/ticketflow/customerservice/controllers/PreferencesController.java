package com.ticketflow.customerservice.controllers;

import com.ticketflow.customerservice.dto.PreferencesRequest;
import com.ticketflow.customerservice.dto.PreferencesResponse;
import com.ticketflow.customerservice.service.PreferencesService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers/{id}/preferences")
public class PreferencesController {

    private final PreferencesService service;

    public PreferencesController(PreferencesService service) {
        this.service = service;
    }

    @GetMapping
    PreferencesResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping
    PreferencesResponse put(@PathVariable Long id, @Valid @RequestBody PreferencesRequest request) {
        return service.put(id, request);
    }
}