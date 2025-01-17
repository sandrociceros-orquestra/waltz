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
import org.finos.waltz.model.command.EntityChangeCommand;
import org.finos.waltz.model.command.FieldChange;
import org.immutables.value.Value;

import java.util.Optional;


@Value.Immutable
@JsonSerialize(as = ImmutableInvolvementKindChangeCommand.class)
@JsonDeserialize(as = ImmutableInvolvementKindChangeCommand.class)
public abstract class InvolvementKindChangeCommand implements EntityChangeCommand {

    public abstract Optional<FieldChange<String>> name();

    public abstract Optional<FieldChange<String>> description();

    public abstract Optional<FieldChange<String>> externalId();

    public abstract Optional<FieldChange<Boolean>> userSelectable();

    public abstract Optional<FieldChange<String>> permittedRole();

    public abstract Optional<FieldChange<Boolean>> transitive();

}
