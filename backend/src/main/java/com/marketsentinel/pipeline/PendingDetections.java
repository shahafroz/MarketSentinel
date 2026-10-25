package com.marketsentinel.pipeline;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Trading days that received new data and need detection to run again. Many trades for the same
 * day collapse into one entry, so a burst of 10,000 trades triggers one detection run, not 10,000.
 */
@Component
public class PendingDetections {

    public record TenantDay(UUID tenantId, LocalDate date) {
    }

    private final Set<TenantDay> pending = ConcurrentHashMap.newKeySet();

    public void mark(UUID tenantId, LocalDate date) {
        pending.add(new TenantDay(tenantId, date));
    }

    public List<TenantDay> drain() {
        List<TenantDay> out = new ArrayList<>();
        for (TenantDay day : List.copyOf(pending)) {
            if (pending.remove(day)) {
                out.add(day);
            }
        }
        return out;
    }
}
