package com.marketsentinel.detection;

import com.marketsentinel.alert.Pattern;
import java.util.List;

/** One market-abuse pattern. Implementations are pure functions of a trading day. */
public interface Detector {

    Pattern pattern();

    List<Finding> detect(MarketDay day);
}
