package com.karthikv.margin.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.karthikv.margin.domain.PercentOfNotionalRule;
import com.karthikv.margin.domain.Side;
import com.karthikv.margin.domain.TradeEvent;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MarginEngineTest {

    private static final BigDecimal RATE = new BigDecimal("0.10");

    private static MarginEngine engine(String defaultCollateral) {
        return new MarginEngine(new PercentOfNotionalRule(RATE), new BigDecimal(defaultCollateral), 1_000);
    }

    private static TradeEvent trade(String id, long seq, String account, String instrument,
                                    Side side, long qty, String price) {
        return new TradeEvent(id, seq, account, instrument, side, qty, new BigDecimal(price));
    }

    @Test
    void firstTradeCreatesPositionAndMargin() {
        MarginEngine engine = engine("1000000");
        EngineUpdate update = engine.onTrade(trade("e1", 1, "A", "X", Side.BUY, 10, "100"));

        assertEquals(EngineUpdate.Outcome.APPLIED, update.outcome());
        assertEquals(0, new BigDecimal("100.00").compareTo(engine.marginRequired("A")));
        assertEquals(10L, engine.netQuantity("A", "X"));
        assertEquals(1, update.exposures().size());
    }

    @Test
    void sellReducesNetQuantityAndMargin() {
        MarginEngine engine = engine("1000000");
        engine.onTrade(trade("e1", 1, "A", "X", Side.BUY, 10, "100"));
        engine.onTrade(trade("e2", 2, "A", "X", Side.SELL, 4, "100"));

        assertEquals(6L, engine.netQuantity("A", "X"));
        assertEquals(0, new BigDecimal("60.00").compareTo(engine.marginRequired("A")));
    }

    @Test
    void shortPositionStillRequiresMargin() {
        MarginEngine engine = engine("1000000");
        engine.onTrade(trade("e1", 1, "A", "X", Side.SELL, 10, "100"));

        assertEquals(-10L, engine.netQuantity("A", "X"));
        assertEquals(0, new BigDecimal("100.00").compareTo(engine.marginRequired("A")));
    }

    @Test
    void duplicateEventIdIsIgnored() {
        MarginEngine engine = engine("1000000");
        TradeEvent first = trade("e1", 1, "A", "X", Side.BUY, 10, "100");
        engine.onTrade(first);
        EngineUpdate again = engine.onTrade(first);

        assertEquals(EngineUpdate.Outcome.DUPLICATE, again.outcome());
        assertEquals(10L, engine.netQuantity("A", "X"));
        assertTrue(again.exposures().isEmpty());
        assertTrue(again.marginCalls().isEmpty());
    }

    @Test
    void lateEventWithOlderSequenceIsIgnored() {
        MarginEngine engine = engine("1000000");
        engine.onTrade(trade("e2", 2, "A", "X", Side.BUY, 10, "100"));
        EngineUpdate late = engine.onTrade(trade("e1", 1, "A", "X", Side.BUY, 99, "50"));

        assertEquals(EngineUpdate.Outcome.STALE, late.outcome());
        assertEquals(10L, engine.netQuantity("A", "X"));
        assertEquals(0, new BigDecimal("100.00").compareTo(engine.marginRequired("A")));
    }

    @Test
    void marginCallIsRaisedOnceUntilAccountRecovers() {
        MarginEngine engine = engine("1000000");
        engine.setCollateral("A", new BigDecimal("1000"));

        EngineUpdate first = engine.onTrade(trade("e1", 1, "A", "X", Side.BUY, 100, "120")); // 1200 > 1000
        assertEquals(1, first.marginCalls().size());
        assertEquals("A#1", first.marginCalls().get(0).callId());

        EngineUpdate stillBreached = engine.onTrade(trade("e2", 2, "A", "X", Side.BUY, 10, "120"));
        assertTrue(stillBreached.marginCalls().isEmpty());
        assertTrue(stillBreached.exposures().get(0).inBreach());

        EngineUpdate recovered = engine.onTrade(trade("e3", 3, "A", "X", Side.SELL, 100, "120")); // 120 < 1000
        assertTrue(recovered.marginCalls().isEmpty());
        assertFalse(recovered.exposures().get(0).inBreach());

        EngineUpdate again = engine.onTrade(trade("e4", 4, "A", "X", Side.BUY, 100, "120"));
        assertEquals(1, again.marginCalls().size());
        assertEquals("A#2", again.marginCalls().get(0).callId());
    }

    @Test
    void repricingAnInstrumentUpdatesEveryHolder() {
        MarginEngine engine = engine("1000000");
        engine.setCollateral("B", new BigDecimal("150"));
        engine.onTrade(trade("e1", 1, "B", "X", Side.BUY, 10, "100")); // margin 100, covered
        EngineUpdate update = engine.onTrade(trade("e2", 1, "A", "X", Side.BUY, 1, "200")); // price -> 200

        assertEquals(2, update.exposures().size());
        assertEquals(0, new BigDecimal("200.00").compareTo(engine.marginRequired("B"))); // B re-priced
        assertEquals(1, update.marginCalls().size());
        assertEquals("B#1", update.marginCalls().get(0).callId());
    }

    @Test
    void onlyHoldersOfTheTradedInstrumentAreRecomputed() {
        MarginEngine engine = engine("1000000");
        engine.onTrade(trade("e1", 1, "A", "X", Side.BUY, 1, "100"));
        engine.onTrade(trade("e2", 1, "A", "Y", Side.BUY, 1, "100"));
        engine.onTrade(trade("e3", 1, "B", "Y", Side.BUY, 1, "100"));

        long before = engine.positionsRecomputed();
        engine.onTrade(trade("e4", 2, "A", "X", Side.BUY, 1, "101")); // only A holds X
        assertEquals(1L, engine.positionsRecomputed() - before);

        long beforeY = engine.positionsRecomputed();
        engine.onTrade(trade("e5", 2, "A", "Y", Side.BUY, 1, "101")); // A and B hold Y
        assertEquals(2L, engine.positionsRecomputed() - beforeY);
    }

    @Test
    void incrementalTotalsMatchFullRecomputeAfterManyTrades() {
        MarginEngine engine = engine("1000000");
        Random random = new Random(42);
        String[] accounts = {"A", "B", "C"};
        String[] instruments = {"X", "Y", "Z", "W", "V"};
        long[][] sequence = new long[accounts.length][instruments.length];

        for (int i = 0; i < 5_000; i++) {
            int a = random.nextInt(accounts.length);
            int in = random.nextInt(instruments.length);
            sequence[a][in]++;
            Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
            String price = String.valueOf(90 + random.nextInt(20));
            engine.onTrade(trade("e" + i, sequence[a][in], accounts[a], instruments[in],
                    side, 1 + random.nextInt(50), price));
        }

        Map<String, BigDecimal> full = engine.fullRecomputeAll();
        for (String account : accounts) {
            assertEquals(0, full.get(account).compareTo(engine.marginRequired(account)));
        }
    }

    @Test
    void dedupeWindowEvictsOldestEventIds() {
        MarginEngine small = new MarginEngine(new PercentOfNotionalRule(RATE), new BigDecimal("1000000"), 2);
        small.onTrade(trade("e1", 1, "A", "X", Side.BUY, 1, "100"));
        small.onTrade(trade("e2", 1, "B", "X", Side.BUY, 1, "100"));
        small.onTrade(trade("e3", 1, "C", "X", Side.BUY, 1, "100")); // evicts e1

        // e1 is outside the window, so it is no longer remembered as a duplicate id; the sequence guard
        // still stops it from being applied twice.
        EngineUpdate replay = small.onTrade(trade("e1", 1, "A", "X", Side.BUY, 1, "100"));
        assertEquals(EngineUpdate.Outcome.STALE, replay.outcome());
        assertEquals(1L, small.netQuantity("A", "X"));
    }
}
