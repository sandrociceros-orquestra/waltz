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

package org.finos.waltz.model.logical_flow;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.finos.waltz.model.application.AssessmentsView;
import org.finos.waltz.model.datatype.DataType;
import org.finos.waltz.model.datatype.DataTypeDecorator;
import org.finos.waltz.model.flow_classification.FlowClassification;
import org.immutables.value.Value;

import java.util.Set;


@Value.Immutable
@JsonSerialize(as = ImmutableDataTypeDecoratorView.class)
@JsonDeserialize(as = ImmutableDataTypeDecoratorView.class)
public interface DataTypeDecoratorView {
    Set<DataTypeDecorator> dataTypeDecorators();
    Set<DataType> dataTypes();
    Set<FlowClassification> classifications();
    AssessmentsView primaryAssessments();
    FlowClassificationRulesView flowClassificationRules();

}
