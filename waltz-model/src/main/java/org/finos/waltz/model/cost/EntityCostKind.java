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

package org.finos.waltz.model.cost;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.finos.waltz.model.*;
import org.immutables.value.Value;


@Value.Immutable
@JsonSerialize(as = ImmutableEntityCostKind.class)
@JsonDeserialize(as = ImmutableEntityCostKind.class)
public abstract class EntityCostKind implements IdProvider, NameProvider, DescriptionProvider, ExternalIdProvider, EntityKindProvider {

    public abstract boolean isDefault();
    public abstract EntityKind subjectKind();

    @Value.Default
    public EntityKind kind() { return EntityKind.COST_KIND; }

}
