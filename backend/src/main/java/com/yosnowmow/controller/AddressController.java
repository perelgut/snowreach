package com.yosnowmow.controller;

import com.yosnowmow.dto.AddressValidateRequest;
import com.yosnowmow.dto.AddressValidateResponse;
import com.yosnowmow.service.GeocodingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/address")
public class AddressController {

    private final GeocodingService geocodingService;

    public AddressController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    @PostMapping("/validate")
    public ResponseEntity<AddressValidateResponse> validate(
            @Valid @RequestBody AddressValidateRequest req) {
        try {
            geocodingService.geocode(req.getAddressText());
            return ResponseEntity.ok(new AddressValidateResponse(true, req.getAddressText()));
        } catch (GeocodingService.GeocodingException ex) {
            return ResponseEntity.ok(new AddressValidateResponse(false, null));
        }
    }
}
