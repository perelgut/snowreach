package com.yosnowmow.dto;

public class AddressValidateResponse {

    private final boolean valid;
    private final String resolvedAddress;   // null when valid=false

    public AddressValidateResponse(boolean valid, String resolvedAddress) {
        this.valid = valid;
        this.resolvedAddress = resolvedAddress;
    }

    public boolean isValid() { return valid; }
    public String getResolvedAddress() { return resolvedAddress; }
}
