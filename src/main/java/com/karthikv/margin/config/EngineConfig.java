package com.karthikv.margin.config;

import com.karthikv.margin.domain.MarginRule;
import com.karthikv.margin.domain.PercentOfNotionalRule;
import com.karthikv.margin.engine.MarginEngine;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {

    @Bean
    MarginRule marginRule(@Value("${margin.rate}") BigDecimal rate) {
        return new PercentOfNotionalRule(rate);
    }

    @Bean
    MarginEngine marginEngine(
            MarginRule rule,
            @Value("${margin.default-collateral}") BigDecimal defaultCollateral,
            @Value("${margin.dedupe-window}") int dedupeWindow) {
        return new MarginEngine(rule, defaultCollateral, dedupeWindow);
    }
}
