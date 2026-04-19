package com.yosnowmow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * @deprecated Sequential dispatch was retired in v1.1 (negotiated-marketplace workflow).
 *
 * This service previously managed a sequential 10-minute offer loop where the platform
 * pushed offers to Workers one at a time.  The new flow uses {@link OfferService} for
 * Worker-initiated offers and bilateral negotiation.
 *
 * This stub is retained so that any Spring beans or test mocks that reference
 * {@code DispatchService} continue to compile.  No business logic remains here.
 * Remove this class entirely once all callers are migrated to {@link OfferService}.
 */
@Deprecated
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    public DispatchService() {
        log.info("DispatchService instantiated as deprecated stub — use OfferService instead");
    }
}
