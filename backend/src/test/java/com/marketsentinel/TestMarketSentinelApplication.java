package com.marketsentinel;

import org.springframework.boot.SpringApplication;

public class TestMarketSentinelApplication {

	public static void main(String[] args) {
		SpringApplication.from(MarketSentinelApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
