package com.marketsentinel.simulator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.marketsentinel.audit.AuditService;
import com.marketsentinel.config.SentinelProperties;
import com.marketsentinel.detection.DetectionService;
import com.marketsentinel.market.TradeRepository;
import com.marketsentinel.web.ApiException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SimulatorServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DetectionService detection = mock(DetectionService.class);
    private final AuditService audit = mock(AuditService.class);

    private SimulatorService service(int maxDays) {
        SentinelProperties.Detection detectionConfig = mock(SentinelProperties.Detection.class);
        when(detectionConfig.marketZone()).thenReturn(ZoneId.of("America/New_York"));
        SentinelProperties props = new SentinelProperties(null, null, List.of(), 0, null,
                new SentinelProperties.Demo(false, null, maxDays), detectionConfig, null, null, null);
        return new SimulatorService(jdbc, mock(TradeRepository.class), detection, audit, props);
    }

    private void alreadySimulatedDays(long days) {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(days);
    }

    @Test
    void refusesOnceTheFirmReachesItsCap() {
        alreadySimulatedDays(30);

        assertThatThrownBy(() -> service(30).simulate(TENANT, "analyst", MONDAY))
                .isInstanceOf(ApiException.class).hasMessageContaining("limit of 30");
        verifyNoInteractions(detection, audit);
    }

    @Test
    void allowsTheLastDayUnderTheCap() {
        alreadySimulatedDays(29);

        service(30).simulate(TENANT, "analyst", MONDAY);

        verify(detection).runForDay(TENANT, MONDAY);
    }

    @Test
    void zeroMeansNoCap() {
        alreadySimulatedDays(5_000);

        service(0).simulate(TENANT, "analyst", MONDAY);

        verify(detection).runForDay(TENANT, MONDAY);
    }
}
