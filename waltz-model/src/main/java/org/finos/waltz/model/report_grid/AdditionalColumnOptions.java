package org.finos.waltz.model.report_grid;

import org.finos.waltz.model.EntityKind;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.finos.waltz.common.SetUtilities.asSet;
import static org.finos.waltz.common.SetUtilities.union;

public enum AdditionalColumnOptions {

    NONE(),
    PICK_HIGHEST(asSet(EntityKind.MEASURABLE)),
    PICK_LOWEST(asSet(EntityKind.MEASURABLE)),
    ROLLUP(asSet(EntityKind.DATA_TYPE)),
    COMPLETED_AND_APPROVED_ONLY(asSet(EntityKind.SURVEY_INSTANCE, EntityKind.SURVEY_QUESTION)),
    EXCLUDE_WITHDRAWN(asSet(EntityKind.SURVEY_INSTANCE, EntityKind.SURVEY_QUESTION)),
    PRIMARY(asSet(EntityKind.MEASURABLE)),
    VALUES_ONLY(asSet(EntityKind.ENTITY_STATISTIC)),
    VALUES_AND_OUTCOMES(asSet(EntityKind.ENTITY_STATISTIC));


    private final Set<EntityKind> allowedKinds;


    AdditionalColumnOptions(Set<EntityKind> allowedKinds) {
        this.allowedKinds = allowedKinds;
    }

    AdditionalColumnOptions() {
        this.allowedKinds = asSet(EntityKind.values());
    }


    public Set<EntityKind> allowedKinds() {
        return allowedKinds;
    }

    public static AdditionalColumnOptions parseColumnOptions(String columnOptions) {
        return Stream
                .of(values())
                .filter(d -> d.name().equals(columnOptions))
                .findFirst()
                .orElse(NONE);
    }

    public static Set<AdditionalColumnOptions> findAllowedKinds(EntityKind entityKind) {
        return union(
                asSet(AdditionalColumnOptions.NONE),
                Arrays.stream(AdditionalColumnOptions.values())
                        .filter(d -> d.allowedKinds().contains(entityKind))
                        .collect(Collectors.toSet()));
    }
}
