/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific
 *
 */

package org.finos.waltz.model.report_grid;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.finos.waltz.model.CommentProvider;
import org.finos.waltz.model.Nullable;
import org.immutables.value.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.finos.waltz.common.SetUtilities.asSet;

@Value.Immutable
@JsonSerialize(as = ImmutableReportGridCell.class)
@JsonDeserialize(as = ImmutableReportGridCell.class)
public abstract class ReportGridCell implements CommentProvider {

    public abstract Long columnDefinitionId();


    public abstract long subjectId(); // y


    public abstract Set<Long> ratingIdValues();

    @Nullable
    public abstract BigDecimal numberValue();


    @Nullable
    public abstract String textValue();

    @Nullable
    public abstract String errorValue();

    @Nullable
    public abstract LocalDateTime dateTimeValue();

    @Value.Default
    public Set<CellOption> options() {
        return asSet(CellOption.defaultCellOption());
    }
}