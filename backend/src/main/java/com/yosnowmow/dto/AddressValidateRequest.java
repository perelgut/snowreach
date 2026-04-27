package com.yosnowmow.dto;

import jakarta.validation.constraints.NotBlank;

public class AddressValidateRequest {

    @NotBlank(message = "addressText is required")
    private String addressText;

    public String getAddressText() { return addressText; }
    public void setAddressText(String addressText) { this.addressText = addressText; }
}
