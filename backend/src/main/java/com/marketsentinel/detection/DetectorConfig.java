package com.marketsentinel.detection;

import com.marketsentinel.config.SentinelProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the detectors from the thresholds in application.yml, so they can be tuned without code changes. */
@Configuration
public class DetectorConfig {

    @Bean
    List<Detector> detectors(SentinelProperties props) {
        var d = props.detection();
        return List.of(
                new WashTradingDetector(d.washMinTrades()),
                new SpoofingDetector(d.spoofLargeMultiple(), d.spoofCancelSeconds(), d.spoofMinCancels(),
                        d.spoofOppositeWindowSeconds()),
                new MarkingTheCloseDetector(d.closeWindowMinutes(), d.closeMinAccountShare(), d.closeMinMarketShare(),
                        d.closeMinMove()),
                new PumpAndDumpDetector(d.pumpMinRise(), d.pumpMinSellShare()),
                new FrontRunningDetector(d.frontRunWindowMinutes(), d.frontRunLargeQty()));
    }

    @Bean
    InsiderBeforeNewsDetector insiderBeforeNewsDetector(SentinelProperties props) {
        return new InsiderBeforeNewsDetector(props.detection().insiderDaysBefore(), props.detection().marketZone());
    }
}
