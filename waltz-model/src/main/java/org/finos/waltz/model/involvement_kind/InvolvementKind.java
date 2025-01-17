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

package org.finos.waltz.model.involvement_kind;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.finos.waltz.model.*;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableInvolvementKind.class)
@JsonDeserialize(as = ImmutableInvolvementKind.class)
public abstract class InvolvementKind implements
        IdProvider,
        NameProvider,
        ExternalIdProvider,
        DescriptionProvider,
        LastUpdatedProvider {

    @Value.Default
    public EntityKind kind() {
        return EntityKind.INVOLVEMENT_KIND;
    }

    public abstract EntityKind subjectKind();

    @Value.Default
    public boolean userSelectable() {
        return true;
    }

    @Nullable
    public abstract String permittedRole();

    @Value.Default
    public boolean transitive() {
        return true;
    }
}
