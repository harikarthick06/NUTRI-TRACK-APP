package com.nutritrack.food.provider;

import com.nutritrack.common.ApiException;
import org.springframework.http.HttpStatus;

/** The external provider failed or is unreachable; reported as 502 without leaking provider details. */
public class ProviderException extends ApiException {
    public ProviderException(String providerName) {
        super(HttpStatus.BAD_GATEWAY, "PROVIDER_UNAVAILABLE",
                providerName + " is unavailable right now. Local foods still work; try again later.");
    }
}
