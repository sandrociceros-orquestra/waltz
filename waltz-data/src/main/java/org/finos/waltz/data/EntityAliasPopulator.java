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

package org.finos.waltz.data;

import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.EntityReference;
import org.finos.waltz.schema.Tables;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.SelectConditionStep;
import org.jooq.SelectOrderByStep;
import org.jooq.Table;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Set;

import static java.lang.String.format;
import static java.util.stream.Collectors.toMap;
import static org.finos.waltz.common.Checks.checkNotNull;
import static org.finos.waltz.model.EntityReference.mkRef;
import static org.finos.waltz.schema.Tables.APPLICATION;
import static org.finos.waltz.schema.Tables.CHANGE_INITIATIVE;
import static org.finos.waltz.schema.Tables.LEGAL_ENTITY;
import static org.finos.waltz.schema.Tables.ORGANISATIONAL_UNIT;
import static org.finos.waltz.schema.Tables.PERSON;
import static org.finos.waltz.schema.Tables.MEASURABLE;
import static org.finos.waltz.schema.Tables.PHYSICAL_SPECIFICATION;
import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * Service which takes an entity kind and returns aliases
 */
@Repository
public class EntityAliasPopulator {

    private final DSLContext dsl;


    @Autowired
    public EntityAliasPopulator(DSLContext dsl) {
        this.dsl = dsl;
    }


    public Map<String, Long> fetchEntityIdLookupMap(EntityKind entityKind, Set<String> identifiers) {
        checkNotNull(entityKind, "entityKind cannot be null");

        switch (entityKind) {
            case APPLICATION:
                return fetchApplicationAliasToIdMap(identifiers);
            case LEGAL_ENTITY:
                return fetchLegalEntityAliasToIdMap(identifiers);
            case PERSON:
                return fetchPersonAliasToIdMap(identifiers);
            case CHANGE_INITIATIVE:
                return fetchChangeInitiativeAliasToIdMap(identifiers);
            case ORG_UNIT:
                return fetchOrgUnitAliasToIdMap(identifiers);
            case MEASURABLE:
                return fetchMeasurableAliasToIdMap(identifiers);
            default:
                throw new IllegalArgumentException(format("Cannot find lookup map for id for entity kind: %s", entityKind));
        }
    }

    private Map<String, Long> fetchMeasurableAliasToIdMap(Set<String> identifiers) {
        return resolveSimpleAliases(identifiers, MEASURABLE, MEASURABLE.EXTERNAL_ID, MEASURABLE.ID);
    }


    private Map<String, Long> fetchChangeInitiativeAliasToIdMap(Set<String> aliases) {
        return resolveSimpleAliases(aliases, CHANGE_INITIATIVE, CHANGE_INITIATIVE.EXTERNAL_ID, CHANGE_INITIATIVE.ID);
    }


    private Map<String, Long> fetchOrgUnitAliasToIdMap(Set<String> aliases) {
        return resolveSimpleAliases(aliases, ORGANISATIONAL_UNIT, ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID);
    }


    private Map<String, Long> fetchApplicationAliasToIdMap(Set<String> aliases) {
        return resolveSimpleAliases(aliases, APPLICATION, APPLICATION.ASSET_CODE, APPLICATION.ID);
    }


    public Map<String, EntityReference> fetchEntityReferenceLookupMap(EntityKind entityKind, Set<String> identifiers) {
        checkNotNull(entityKind, "entityKind cannot be null");

        switch (entityKind) {
            case APPLICATION:
                return fetchApplicationAliasToReferenceMap(identifiers);
            case LEGAL_ENTITY:
                return fetchLegalEntityAliasToReferenceMap(identifiers);
            default:
                throw new IllegalArgumentException(format("Cannot find lookup map for entity reference for entity kind: %s", entityKind));
        }
    }


    private Map<String, EntityReference> fetchApplicationAliasToReferenceMap(Set<String> aliases) {
        return dsl
                .select(Tables.APPLICATION.ASSET_CODE, Tables.APPLICATION.ID, Tables.APPLICATION.NAME)
                .from(Tables.APPLICATION)
                .where(Tables.APPLICATION.ASSET_CODE.in(aliases))
                .fetchSet(r -> tuple(r.get(Tables.APPLICATION.ASSET_CODE), r.get(Tables.APPLICATION.ID), r.get(Tables.APPLICATION.NAME)))
                .stream()
                .collect(toMap(t -> t.v1, t -> mkRef(EntityKind.APPLICATION, t.v2, t.v3), (v1, v2) -> v1));
    }


    private Map<String, Long> fetchLegalEntityAliasToIdMap(Set<String> aliases) {
        return resolveSimpleAliases(aliases, LEGAL_ENTITY, LEGAL_ENTITY.EXTERNAL_ID, LEGAL_ENTITY.ID);
    }


    private Map<String, EntityReference> fetchLegalEntityAliasToReferenceMap(Set<String> aliases) {
        return dsl
                .select(Tables.LEGAL_ENTITY.EXTERNAL_ID, Tables.LEGAL_ENTITY.ID, Tables.LEGAL_ENTITY.NAME)
                .from(Tables.LEGAL_ENTITY)
                .where(Tables.LEGAL_ENTITY.EXTERNAL_ID.in(aliases))
                .fetchSet(r -> tuple(r.get(Tables.LEGAL_ENTITY.EXTERNAL_ID), r.get(Tables.LEGAL_ENTITY.ID), r.get(Tables.LEGAL_ENTITY.NAME)))
                .stream()
                .collect(toMap(t -> t.v1, t -> mkRef(EntityKind.LEGAL_ENTITY, t.v2, t.v3), (v1, v2) -> v1));
    }


    private Map<String, Long> fetchPersonAliasToIdMap(Set<String> aliases) {

        Condition isActive = PERSON.IS_REMOVED.isFalse();

        SelectConditionStep<Record2<String, Long>> emails = mkQry(aliases, PERSON, PERSON.EMAIL, PERSON.ID)
                .and(isActive);

        SelectConditionStep<Record2<String, Long>> name = mkQry(aliases, PERSON, PERSON.DISPLAY_NAME, PERSON.ID)
                .and(isActive);

        SelectConditionStep<Record2<String, Long>> empId = mkQry(aliases, PERSON, PERSON.EMPLOYEE_ID, PERSON.ID)
                .and(isActive);

        SelectOrderByStep<Record2<String, Long>> qry = emails.union(name.union(empId));

        return qry
                .fetchSet(r -> tuple(r.get(0, String.class), r.get(PERSON.ID)))
                .stream()
                .collect(toMap(t -> t.v1, t -> t.v2, (v1, v2) -> v1));
    }


    // --- HELPERS ---

    private Map<String, Long> resolveSimpleAliases(Set<String> aliases,
                                                   Table<?> table,
                                                   Field<String> externalIdField,
                                                   Field<Long> idField) {
        return mkQry(aliases, table, externalIdField, idField)
                .fetchSet(r -> tuple(
                        r.get(externalIdField),
                        r.get(idField)))
                .stream()
                .collect(toMap(
                        t -> t.v1,
                        t -> t.v2,
                        (v1, v2) -> v1));
    }


    private SelectConditionStep<Record2<String, Long>> mkQry(Set<String> aliases,
                                                             Table<?> table,
                                                             Field<String> externalIdField,
                                                             Field<Long> idField) {
        return dsl
                .select(externalIdField, idField)
                .from(table)
                .where(externalIdField.in(aliases));
    }

}
