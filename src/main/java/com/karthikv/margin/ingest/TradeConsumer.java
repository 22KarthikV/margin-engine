package com.karthikv.margin.ingest;

import com.karthikv.margin.api.ExposurePublisher;
import com.karthikv.margin.domain.TradeEvent;
import com.karthikv.margin.engine.EngineUpdate;
import com.karthikv.margin.engine.MarginEngine;
import com.karthikv.margin.persistence.MarginCallEntity;
import com.karthikv.margin.persistence.MarginCallRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka entry point. Delivery is at-least-once, so the same trade can arrive more than once; the engine
 * ignores repeats and the call table is keyed by call id, which keeps the whole path idempotent.
 *
 * <p>Runs with the default listener concurrency of 1 because the engine is a single writer. To scale out,
 * partition the topic by instrument and give each partition its own engine instance.
 */
@Component
public class TradeConsumer {

    private final MarginEngine engine;
    private final ExposurePublisher publisher;
    private final MarginCallRepository marginCalls;

    public TradeConsumer(MarginEngine engine, ExposurePublisher publisher, MarginCallRepository marginCalls) {
        this.engine = engine;
        this.publisher = publisher;
        this.marginCalls = marginCalls;
    }

    @KafkaListener(
            topics = "${margin.topic.trades}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onTrade(TradeEvent trade) {
        EngineUpdate update = engine.onTrade(trade);
        update.marginCalls().forEach(call -> marginCalls.save(MarginCallEntity.from(call)));
        publisher.publish(update.exposures());
    }
}
