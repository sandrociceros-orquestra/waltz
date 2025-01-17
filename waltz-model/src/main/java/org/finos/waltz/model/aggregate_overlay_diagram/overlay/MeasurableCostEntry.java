package org.finos.waltz.model.aggregate_overlay_diagram.overlay;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.finos.waltz.model.Nullable;
import org.immutables.value.Value;

import java.math.BigDecimal;

@Value.Immutable
@JsonSerialize(as = ImmutableMeasurableCostEntry.class)
public abstract class MeasurableCostEntry {

    public abstract long appId();

    public abstract long measurableId();

    public abstract long measurableRatingId();

    @Nullable
    public abstract Long costKindId();

    @Nullable
    public abstract Integer year();

    @Nullable
    public abstract Integer allocationPercentage();

    @Nullable
    public abstract BigDecimal overallCost();

    @Nullable
    public abstract BigDecimal allocatedCost();
    public abstract AllocationDerivation allocationDerivation();
}