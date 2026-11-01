package com.karthikv.margin.engine;

import com.karthikv.margin.domain.AccountExposure;
import com.karthikv.margin.domain.MarginCall;
import java.util.List;

/** Result of feeding one trade to the engine. */
public record EngineUpdate(Outcome outcome, List<AccountExposure> exposures, List<MarginCall> marginCalls) {

    public enum Outcome {
        /** The trade changed engine state. */
        APPLIED,
        /** Same eventId seen before; ignored. */
        DUPLICATE,
        /** Sequence not newer than the last applied for that position; ignored. */
        STALE
    }

    static EngineUpdate ignored(Outcome outcome) {
        return new EngineUpdate(outcome, List.of(), List.of());
    }
}
