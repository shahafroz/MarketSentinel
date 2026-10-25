package com.marketsentinel.pipeline;

import com.marketsentinel.detection.DetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 10 seconds, re-runs detection for each trading day that received new data. */
@Component
public class DetectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DetectionScheduler.class);

    private final PendingDetections pending;
    private final DetectionService detection;

    public DetectionScheduler(PendingDetections pending, DetectionService detection) {
        this.pending = pending;
        this.detection = detection;
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void runPending() {
        for (PendingDetections.TenantDay day : pending.drain()) {
            try {
                detection.runForDay(day.tenantId(), day.date());
            } catch (RuntimeException e) {
                log.error("Detection failed for {}; will retry on the next run", day, e);
                pending.mark(day.tenantId(), day.date());
            }
        }
    }
}
