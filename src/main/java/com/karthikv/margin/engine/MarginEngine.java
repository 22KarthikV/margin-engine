package com.karthikv.margin.engine;

import com.karthikv.margin.domain.AccountExposure;
import com.karthikv.margin.domain.MarginCall;
import com.karthikv.margin.domain.MarginRule;
import com.karthikv.margin.domain.PositionKey;
import com.karthikv.margin.domain.TradeEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Incremental margin engine.
 *
 * <p>Design:
 * <ul>
 *   <li><b>Incremental.</b> Each position stores its last requirement. A trade re-prices only the
 *       positions in the traded instrument and adjusts each account total by the difference, so work
 *       per trade is proportional to the positions in that instrument, not to all positions.</li>
 *   <li><b>Idempotent.</b> A repeated {@code eventId} and any event whose {@code sequence} is not newer
 *       than the position's last applied sequence are ignored, so duplicates and late events can
 *       neither move margin nor raise a second call.</li>
 *   <li><b>One call per breach.</b> A margin call is raised only on the transition from covered to
 *       under-collateralised, with a deterministic id.</li>
 *   <li><b>Single writer.</b> All mutation is synchronised on the engine. Scale-out would partition
 *       by account and instrument; see the README.</li>
 * </ul>
 *
 * <p>The class has no Spring or Kafka dependency, so it can be unit tested and benchmarked on its own.
 */
public final class MarginEngine {

    private final MarginRule rule;
    private final BigDecimal defaultCollateral;
    private final Map<String, BigDecimal> collateralByAccount = new HashMap<>();
    private final Map<PositionKey, PositionState> positions = new HashMap<>();
    private final Map<String, Set<PositionKey>> positionsByInstrument = new HashMap<>();
    private final Map<String, AccountState> accounts = new HashMap<>();
    private final Map<String, BigDecimal> lastPrice = new HashMap<>();
    private final Set<String> seenEventIds;
    private long positionsRecomputed;

    public MarginEngine(MarginRule rule, BigDecimal defaultCollateral, int dedupeWindow) {
        if (dedupeWindow <= 0) {
            throw new IllegalArgumentException("dedupeWindow must be positive");
        }
        this.rule = rule;
        this.defaultCollateral = defaultCollateral;
        this.seenEventIds = Collections.newSetFromMap(new LinkedHashMap<String, Boolean>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > dedupeWindow;
            }
        });
    }

    public synchronized void setCollateral(String account, BigDecimal amount) {
        collateralByAccount.put(account, amount);
    }

    public synchronized EngineUpdate onTrade(TradeEvent trade) {
        if (!seenEventIds.add(trade.eventId())) {
            return EngineUpdate.ignored(EngineUpdate.Outcome.DUPLICATE);
        }

        PositionKey key = new PositionKey(trade.account(), trade.instrument());
        PositionState state = positions.get(key);
        if (state != null && trade.sequence() <= state.lastSequence) {
            return EngineUpdate.ignored(EngineUpdate.Outcome.STALE);
        }
        if (state == null) {
            state = new PositionState();
            positions.put(key, state);
            positionsByInstrument.computeIfAbsent(trade.instrument(), i -> new HashSet<>()).add(key);
            accounts.computeIfAbsent(trade.account(), a -> new AccountState());
        }
        state.netQuantity += trade.signedQuantity();
        state.lastSequence = trade.sequence();
        lastPrice.put(trade.instrument(), trade.price());

        // The trade price becomes the instrument's price, so every holder of the instrument is re-priced.
        Set<String> touchedAccounts = new TreeSet<>();
        for (PositionKey holder : positionsByInstrument.get(trade.instrument())) {
            PositionState position = positions.get(holder);
            BigDecimal fresh = rule.requirement(holder.instrument(), position.netQuantity, trade.price());
            AccountState account = accounts.get(holder.account());
            account.marginRequired = account.marginRequired.add(fresh).subtract(position.requirement);
            position.requirement = fresh;
            positionsRecomputed++;
            touchedAccounts.add(holder.account());
        }

        List<AccountExposure> exposures = new ArrayList<>(touchedAccounts.size());
        List<MarginCall> calls = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String accountId : touchedAccounts) {
            AccountState account = accounts.get(accountId);
            BigDecimal collateral = collateralFor(accountId);
            boolean breach = account.marginRequired.compareTo(collateral) > 0;
            if (breach && !account.inBreach) {
                account.breachCount++;
                calls.add(new MarginCall(accountId + "#" + account.breachCount, accountId,
                        account.marginRequired, collateral));
            }
            account.inBreach = breach;
            exposures.add(new AccountExposure(accountId, account.marginRequired, collateral, breach, now));
        }
        return new EngineUpdate(EngineUpdate.Outcome.APPLIED, exposures, calls);
    }

    /** Margin the account requires right now, from the incrementally maintained total. */
    public synchronized BigDecimal marginRequired(String account) {
        AccountState state = accounts.get(account);
        return state == null ? BigDecimal.ZERO : state.marginRequired;
    }

    /**
     * Recomputes every account from scratch using each instrument's latest price. Used to prove the
     * incremental totals agree with a full pass, and as the baseline in the benchmark.
     */
    public synchronized Map<String, BigDecimal> fullRecomputeAll() {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (Map.Entry<PositionKey, PositionState> entry : positions.entrySet()) {
            PositionKey key = entry.getKey();
            BigDecimal requirement = rule.requirement(
                    key.instrument(), entry.getValue().netQuantity, lastPrice.get(key.instrument()));
            totals.merge(key.account(), requirement, BigDecimal::add);
        }
        return totals;
    }

    private BigDecimal collateralFor(String account) {
        return collateralByAccount.getOrDefault(account, defaultCollateral);
    }

    public synchronized long positionsRecomputed() {
        return positionsRecomputed;
    }

    public synchronized int positionCount() {
        return positions.size();
    }

    public synchronized long netQuantity(String account, String instrument) {
        PositionState state = positions.get(new PositionKey(account, instrument));
        return state == null ? 0 : state.netQuantity;
    }

    private static final class PositionState {
        long netQuantity;
        long lastSequence = Long.MIN_VALUE;
        BigDecimal requirement = BigDecimal.ZERO;
    }

    private static final class AccountState {
        BigDecimal marginRequired = BigDecimal.ZERO;
        boolean inBreach;
        long breachCount;
    }
}
