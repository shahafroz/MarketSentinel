package com.marketsentinel.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of("name", "MarketSentinel API", "ok", true, "health", "/actuator/health");
    }
}
