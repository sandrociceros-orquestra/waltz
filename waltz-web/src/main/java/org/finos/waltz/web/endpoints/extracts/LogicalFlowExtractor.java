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

package org.finos.waltz.web.endpoints.extracts;

import org.finos.waltz.common.ListUtilities;
import org.finos.waltz.data.application.ApplicationIdSelectorFactory;
import org.finos.waltz.data.data_type.DataTypeIdSelectorFactory;
import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.FlowDirection;
import org.finos.waltz.model.IdSelectionOptions;
import org.finos.waltz.schema.tables.FlowClassification;
import org.finos.waltz.web.WebUtilities;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.jooq.lambda.tuple.Tuple3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.finos.waltz.common.ListUtilities.isEmpty;
import static org.finos.waltz.common.ListUtilities.newArrayList;
import static org.finos.waltz.data.InlineSelectFieldFactory.mkExternalIdField;
import static org.finos.waltz.data.InlineSelectFieldFactory.mkNameField;
import static org.finos.waltz.model.EntityLifecycleStatus.REMOVED;
import static org.finos.waltz.schema.Tables.*;
import static org.finos.waltz.schema.tables.Application.APPLICATION;
import static org.finos.waltz.schema.tables.LogicalFlow.LOGICAL_FLOW;
import static org.finos.waltz.schema.tables.OrganisationalUnit.ORGANISATIONAL_UNIT;
import static spark.Spark.post;


@Service
public class LogicalFlowExtractor extends CustomDataExtractor {

    private static final Field<String> SOURCE_NAME_FIELD = mkNameField(
            LOGICAL_FLOW.SOURCE_ENTITY_ID,
            LOGICAL_FLOW.SOURCE_ENTITY_KIND,
            newArrayList(EntityKind.APPLICATION, EntityKind.ACTOR, EntityKind.END_USER_APPLICATION));

    private static final Field<String> TARGET_NAME_FIELD = mkNameField(
            LOGICAL_FLOW.TARGET_ENTITY_ID,
            LOGICAL_FLOW.TARGET_ENTITY_KIND,
            newArrayList(EntityKind.APPLICATION, EntityKind.ACTOR, EntityKind.END_USER_APPLICATION));

    private static final Field<String> SOURCE_EXT_ID_FIELD = mkExternalIdField(
            LOGICAL_FLOW.SOURCE_ENTITY_ID,
            LOGICAL_FLOW.SOURCE_ENTITY_KIND,
            newArrayList(EntityKind.APPLICATION, EntityKind.ACTOR, EntityKind.END_USER_APPLICATION));

    private static final Field<String> TARGET_EXT_ID_FIELD = mkExternalIdField(
            LOGICAL_FLOW.TARGET_ENTITY_ID,
            LOGICAL_FLOW.TARGET_ENTITY_KIND,
            newArrayList(EntityKind.APPLICATION, EntityKind.ACTOR, EntityKind.END_USER_APPLICATION));
    public static final Field<Long> LOGICAL_FLOW_ID = LOGICAL_FLOW.ID.as("Waltz Id");

    private static List<String> staticHeaders = newArrayList(
            "Waltz Id",
            "Source",
            "Source Asset Code",
            "Source Org Unit",
            "Target",
            "Target Asset Code",
            "Target Org Unit",
            "Flow External Id",
            "Data Type",
            "Source Outbound Rating",
            "Target Inbound Rating",
            "Created At",
            "Created By",
            "Last Updated At",
            "Last Updated By");

    private final ApplicationIdSelectorFactory applicationIdSelectorFactory = new ApplicationIdSelectorFactory();
    private final DataTypeIdSelectorFactory dataTypeIdSelectorFactory = new DataTypeIdSelectorFactory();

    private final DSLContext dsl;

    @Autowired
    public LogicalFlowExtractor(DSLContext dsl) {
        this.dsl = dsl;
    }


    @Override
    public void register() {
        post(WebUtilities.mkPath("data-extract", "logical-flows"), (request, response) -> {
            IdSelectionOptions options = WebUtilities.readIdSelectionOptionsFromBody(request);

            return writeReportResults(
                    response,
                    prepareFlows(
                            prepareQuery(dsl, options),
                            parseExtractFormat(request),
                            "logical-flows",
                            getTagsMap()));
        });
    }


    private SelectConditionStep<Record> prepareQuery(DSLContext dsl, IdSelectionOptions options) {

        Select<Record1<Long>> appIdSelector = applicationIdSelectorFactory.apply(options);

        Condition conditionForDataType = EntityKind.DATA_TYPE.equals(options.entityReference().kind())
                ? LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID.in(dataTypeIdSelectorFactory.apply(options))
                : DSL.trueCondition();

        Field<String> sourceOrgUnitNameField = DSL
                .when(LOGICAL_FLOW.SOURCE_ENTITY_KIND.eq(EntityKind.APPLICATION.name()),
                        DSL.select(ORGANISATIONAL_UNIT.NAME)
                                .from(APPLICATION)
                                .innerJoin(ORGANISATIONAL_UNIT)
                                    .on(ORGANISATIONAL_UNIT.ID.eq(APPLICATION.ORGANISATIONAL_UNIT_ID))
                                .where(APPLICATION.ID.eq(LOGICAL_FLOW.SOURCE_ENTITY_ID)));

        Field<String> targetOrgUnitNameField = DSL
                .when(LOGICAL_FLOW.TARGET_ENTITY_KIND.eq(EntityKind.APPLICATION.name()),
                        DSL.select(ORGANISATIONAL_UNIT.NAME)
                                .from(APPLICATION)
                                .innerJoin(ORGANISATIONAL_UNIT)
                                    .on(ORGANISATIONAL_UNIT.ID.eq(APPLICATION.ORGANISATIONAL_UNIT_ID))
                                .where(APPLICATION.ID.eq(LOGICAL_FLOW.TARGET_ENTITY_ID)));

        Condition sourceIsApp = LOGICAL_FLOW.SOURCE_ENTITY_ID.in(appIdSelector).and(LOGICAL_FLOW.SOURCE_ENTITY_KIND.eq(EntityKind.APPLICATION.name()));
        Condition targetIsApp = LOGICAL_FLOW.TARGET_ENTITY_ID.in(appIdSelector).and(LOGICAL_FLOW.TARGET_ENTITY_KIND.eq(EntityKind.APPLICATION.name()));

        FlowClassification sourceClassification = FLOW_CLASSIFICATION.as("sourceClassification");
        FlowClassification targetClassification = FLOW_CLASSIFICATION.as("targetClassification");

        SelectConditionStep<Record> qry = dsl
                .select(LOGICAL_FLOW_ID)
                .select(SOURCE_NAME_FIELD.as("Source"),
                        SOURCE_EXT_ID_FIELD.as("Source Asset Code"),
                        sourceOrgUnitNameField.as("Source Org Unit"))
                .select(TARGET_NAME_FIELD.as("Target"),
                        TARGET_EXT_ID_FIELD.as("Target Asset Code"),
                        targetOrgUnitNameField.as("Target Org Unit"))
                .select(LOGICAL_FLOW.EXTERNAL_ID.as("Flow External Id"))
                .select(DATA_TYPE.NAME.as("Data Type"))
                .select(sourceClassification.NAME.as("Source Outbound Rating"))
                .select(targetClassification.NAME.as("Target Inbound Rating"))
                .select(LOGICAL_FLOW.CREATED_AT.as("Created At"))
                .select(LOGICAL_FLOW.CREATED_BY.as("Created By"))
                .select(LOGICAL_FLOW.LAST_UPDATED_AT.as("Last Updated At"))
                .select(LOGICAL_FLOW.LAST_UPDATED_BY.as("Last Updated By"))
                .from(LOGICAL_FLOW)
                .innerJoin(LOGICAL_FLOW_DECORATOR)
                .on(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID.eq(LOGICAL_FLOW.ID)
                        .and(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_KIND.eq(EntityKind.DATA_TYPE.name())))
                .innerJoin(DATA_TYPE)
                .on(DATA_TYPE.ID.eq(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID)
                        .and(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_KIND.eq(EntityKind.DATA_TYPE.name())))
                .innerJoin(sourceClassification)
                .on(sourceClassification.CODE.eq(LOGICAL_FLOW_DECORATOR.RATING)
                        .and(sourceClassification.DIRECTION.eq(FlowDirection.OUTBOUND.name())))
                .innerJoin(targetClassification)
                .on(targetClassification.CODE.eq(LOGICAL_FLOW_DECORATOR.TARGET_INBOUND_RATING)
                        .and(targetClassification.DIRECTION.eq(FlowDirection.INBOUND.name())))
                .where(dsl.renderInlined(LOGICAL_FLOW.ENTITY_LIFECYCLE_STATUS.ne(REMOVED.name())
                .and(LOGICAL_FLOW.IS_REMOVED.isFalse())
                .and(conditionForDataType)
                .and(sourceIsApp.or(targetIsApp))));

        return qry;
    }

    private Tuple3<ExtractFormat, String, byte[]> prepareFlows(SelectConditionStep<Record> query,
                                                               ExtractFormat format,
                                                               String reportName,
                                                               Map<Long, List<String>> tags) throws IOException {

        List<List<Object>> reportRows = prepareReportRows(query, tags);
        return formatReport(
                format,
                reportName,
                reportRows,
                ListUtilities.append(staticHeaders, "Tags")
        );
    }

    private List<List<Object>> prepareReportRows(SelectConditionStep<Record> qry,
                                                 Map<Long, List<String>> tags) {
        Result<Record> results = qry.fetch();

        return results
                .stream()
                .map(row -> {
                    ArrayList<Object> reportRow = new ArrayList<>();
                    staticHeaders.forEach(h -> reportRow.add(row.get(h)));

                    Long logicalFlowId = row.get(LOGICAL_FLOW_ID);
                    List<String> logicalFlowTags = tags.get(logicalFlowId);
                    reportRow.add(isEmpty(logicalFlowTags)
                            ? ""
                            : String.join(",", logicalFlowTags));

                    return reportRow;
                })
                .collect(toList());
    }

    private Map<Long, List<String>> getTagsMap() {
        return dsl.select(PHYSICAL_FLOW.LOGICAL_FLOW_ID, TAG.NAME)
                .from(TAG_USAGE)
                .join(TAG)
                .on(TAG.ID.eq(TAG_USAGE.TAG_ID))
                .join(PHYSICAL_FLOW)
                .on(TAG_USAGE.ENTITY_KIND.eq(EntityKind.PHYSICAL_FLOW.name())
                        .and(PHYSICAL_FLOW.ID.eq(TAG_USAGE.ENTITY_ID)))
                .fetchGroups(PHYSICAL_FLOW.LOGICAL_FLOW_ID, TAG.NAME);
    }
}
