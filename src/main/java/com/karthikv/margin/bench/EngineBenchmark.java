package com.karthikv.margin.bench;

import com.karthikv.margin.domain.PercentOfNotionalRule;
import com.karthikv.margin.domain.Side;
import com.karthikv.margin.domain.TradeEvent;
import com.karthikv.margin.engine.EngineUpdate;
import com.karthikv.margin.engine.MarginEngine;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * In-process benchmark on SYNTHETIC data. It measures the engine alone: no Kafka, no database, no network.
 * Numbers from it describe this engine on this machine and must say so wherever they are quoted.
 *
 * <pre>
 *   java -cp target/classes com.karthikv.margin.bench.EngineBenchmark [positions] [trades] [seed] [accounts]
 * </pre>
 */
public final class EngineBenchmark {

    public static void main(String[] args) {
        int positionCount = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
        int tradeCount = args.length > 1 ? Integer.parseInt(args[1]) : 20_000;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 42L;
        // Accounts also equals the holders per instrument: a trade re-prices every holder of its
        // instrument, so this is the main knob for the per-trade cost. Fewer accounts = cheaper trades.
        int accounts = args.length > 3 ? Integer.parseInt(args[3]) : 100;
        double duplicateRate = 0.02;
        double lateRate = 0.02;

        Random random = new Random(seed);
        // Collateral sits near the average requirement (about 550 per position), so accounts keep
        // crossing the threshold and the "one call per breach" logic is genuinely exercised.
        BigDecimal collateral = BigDecimal.valueOf((long) Math.max(1, positionCount / accounts) * 550);
        MarginEngine engine = new MarginEngine(
                new PercentOfNotionalRule(new BigDecimal("0.10")), collateral, 1_000_000);

        // Build the book: one position per (account, instrument) pair, all at price 100.
        long[] sequence = new long[positionCount];
        long buildStart = System.nanoTime();
        for (int i = 0; i < positionCount; i++) {
            sequence[i] = 1;
            engine.onTrade(new TradeEvent("seed-" + i, 1, account(i, accounts), instrument(i, accounts), Side.BUY,
                    10 + random.nextInt(90), new BigDecimal("100")));
        }
        double buildMs = (System.nanoTime() - buildStart) / 1e6;

        // Trade stream with injected duplicates (same eventId) and late events (older sequence).
        long[] latenciesNs = new long[tradeCount];
        Set<String> callIds = new HashSet<>();
        long calls = 0;
        long duplicateCallIds = 0;
        long applied = 0;
        long duplicates = 0;
        long stale = 0;
        TradeEvent previous = null;
        long streamStart = System.nanoTime();
        for (int n = 0; n < tradeCount; n++) {
            TradeEvent event;
            double roll = random.nextDouble();
            if (previous != null && roll < duplicateRate) {
                event = previous;
            } else {
                int i = random.nextInt(positionCount);
                long seq = roll < duplicateRate + lateRate ? Math.max(0, sequence[i] - 1) : ++sequence[i];
                BigDecimal price = BigDecimal.valueOf(9_500 + random.nextInt(1_000), 2);
                event = new TradeEvent("t-" + n, seq, account(i, accounts), instrument(i, accounts),
                        random.nextBoolean() ? Side.BUY : Side.SELL, 1 + random.nextInt(50), price);
                previous = event;
            }
            long t0 = System.nanoTime();
            EngineUpdate update = engine.onTrade(event);
            latenciesNs[n] = System.nanoTime() - t0;

            switch (update.outcome()) {
                case APPLIED -> applied++;
                case DUPLICATE -> duplicates++;
                case STALE -> stale++;
            }
            for (var call : update.marginCalls()) {
                calls++;
                if (!callIds.add(call.callId())) {
                    duplicateCallIds++;
                }
            }
        }
        double streamSeconds = (System.nanoTime() - streamStart) / 1e9;

        // Baseline and correctness: a full pass over every position, compared with the incremental totals.
        long fullStart = System.nanoTime();
        Map<String, BigDecimal> full = engine.fullRecomputeAll();
        double fullMs = (System.nanoTime() - fullStart) / 1e6;
        long mismatches = full.entrySet().stream()
                .filter(e -> e.getValue().compareTo(engine.marginRequired(e.getKey())) != 0)
                .count();

        Arrays.sort(latenciesNs);
        System.out.println("{");
        System.out.println("  \"dataset\": \"synthetic\",");
        System.out.println("  \"seed\": " + seed + ",");
        System.out.println("  \"positions\": " + engine.positionCount() + ", \"holdersPerInstrument\": " + accounts + ",");
        System.out.println("  \"tradesSent\": " + tradeCount + ",");
        System.out.println("  \"applied\": " + applied + ", \"duplicatesIgnored\": " + duplicates
                + ", \"staleIgnored\": " + stale + ",");
        System.out.println("  \"bookBuildMs\": " + round(buildMs) + ",");
        System.out.println("  \"throughputTradesPerSec\": " + round(tradeCount / streamSeconds) + ",");
        System.out.println("  \"latencyMicros\": {\"p50\": " + micros(latenciesNs, 0.50) + ", \"p99\": "
                + micros(latenciesNs, 0.99) + ", \"max\": " + micros(latenciesNs, 1.0) + "},");
        System.out.println("  \"positionsRecomputedPerAppliedTrade\": "
                + round((double) engine.positionsRecomputed() / (positionCount + applied)) + ",");
        System.out.println("  \"fullRecomputeAllMs\": " + round(fullMs) + ",");
        System.out.println("  \"marginCalls\": " + calls + ", \"duplicateCallIds\": " + duplicateCallIds + ",");
        System.out.println("  \"incrementalVsFullMismatches\": " + mismatches);
        System.out.println("}");
    }

    private static String account(int i, int accounts) {
        return "ACC-" + (i % accounts);
    }

    private static String instrument(int i, int accounts) {
        return "INS-" + (i / accounts);
    }

    private static double micros(long[] sortedNs, double quantile) {
        int index = (int) Math.min(sortedNs.length - 1, Math.ceil(quantile * sortedNs.length) - 1);
        return round(sortedNs[Math.max(0, index)] / 1_000.0);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
