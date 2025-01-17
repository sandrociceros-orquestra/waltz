package org.finos.waltz.model.aggregate_overlay_diagram.overlay;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.Set;

@Value.Immutable
@JsonSerialize(as = ImmutableDateChangeInformation.class)
public abstract class DateChangeInformation {

    public abstract QuarterDetail quarter();

    @Value.Derived
    public int count() {
        return changes().size();
    }
    public abstract Set<AppChangeEntry> changes();

}