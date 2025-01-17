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

package org.finos.waltz.data.entity_relationship;

import org.finos.waltz.common.DateTimeUtilities;
import org.finos.waltz.data.GenericSelector;
import org.finos.waltz.data.InlineSelectFieldFactory;
import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.EntityReference;
import org.finos.waltz.model.ImmutableEntityReference;
import org.finos.waltz.model.entity_relationship.EntityRelationship;
import org.finos.waltz.model.entity_relationship.EntityRelationshipKey;
import org.finos.waltz.model.entity_relationship.ImmutableEntityRelationship;
import org.finos.waltz.model.entity_relationship.RelationshipKind;
import org.finos.waltz.model.entity_relationship.UpdateEntityRelationshipParams;
import org.finos.waltz.schema.Tables;
import org.finos.waltz.schema.tables.records.EntityRelationshipRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Record3;
import org.jooq.RecordMapper;
import org.jooq.SelectConditionStep;
import org.jooq.SelectOrderByStep;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.finos.waltz.common.Checks.checkNotNull;
import static org.finos.waltz.common.DateTimeUtilities.toLocalDateTime;
import static org.finos.waltz.common.ListUtilities.newArrayList;
import static org.finos.waltz.schema.tables.EntityRelationship.ENTITY_RELATIONSHIP;


@Repository
public class EntityRelationshipDao {

    private static final Logger LOG = LoggerFactory.getLogger(EntityRelationshipDao.class);

    private static final List<EntityKind> POSSIBLE_ENTITIES = newArrayList(
            EntityKind.APPLICATION,
            EntityKind.APP_GROUP,
            EntityKind.ACTOR,
            EntityKind.MEASURABLE,
            EntityKind.CHANGE_INITIATIVE);


    private static final Field<String> NAME_A = InlineSelectFieldFactory.mkNameField(
            ENTITY_RELATIONSHIP.ID_A,
            ENTITY_RELATIONSHIP.KIND_A,
            POSSIBLE_ENTITIES);


    private static final Field<String> NAME_B = InlineSelectFieldFactory.mkNameField(
            ENTITY_RELATIONSHIP.ID_B,
            ENTITY_RELATIONSHIP.KIND_B,
            POSSIBLE_ENTITIES);


    private static final Field<String> EXT_ID_A = InlineSelectFieldFactory.mkExternalIdField(
            ENTITY_RELATIONSHIP.ID_A,
            ENTITY_RELATIONSHIP.KIND_A,
            POSSIBLE_ENTITIES);


    private static final Field<String> EXT_ID_B = InlineSelectFieldFactory.mkExternalIdField(
            ENTITY_RELATIONSHIP.ID_B,
            ENTITY_RELATIONSHIP.KIND_B,
            POSSIBLE_ENTITIES);


    private static final RecordMapper<Record, EntityRelationship> TO_DOMAIN_MAPPER = r -> {
        EntityRelationshipRecord record = r.into(ENTITY_RELATIONSHIP);
        return ImmutableEntityRelationship.builder()
                .id(record.getId())
                .a(ImmutableEntityReference.builder()
                        .kind(EntityKind.valueOf(record.getKindA()))
                        .id(record.getIdA())
                        .name(Optional.ofNullable(r.get(NAME_A)).orElse("_Removed_"))
                        .externalId(Optional.ofNullable(r.get(EXT_ID_A)))
                        .build())
                .b(ImmutableEntityReference.builder()
                        .kind(EntityKind.valueOf(record.getKindB()))
                        .id(record.getIdB())
                        .name(Optional.ofNullable(r.get(NAME_B)).orElse("_Removed_"))
                        .externalId(Optional.ofNullable(r.get(EXT_ID_B)))
                        .build())
                .provenance(record.getProvenance())
                .relationship(record.getRelationship())
                .description(record.getDescription())
                .lastUpdatedBy(record.getLastUpdatedBy())
                .lastUpdatedAt(toLocalDateTime(record.getLastUpdatedAt()))
                .build();
    };


    private static final Function<EntityRelationship, EntityRelationshipRecord> TO_RECORD_MAPPER = rel -> {
        EntityRelationshipRecord record = new EntityRelationshipRecord();
        record.setRelationship(rel.relationship());
        record.setIdA(rel.a().id());
        record.setKindA(rel.a().kind().name());
        record.setIdB(rel.b().id());
        record.setKindB(rel.b().kind().name());
        record.setProvenance(rel.provenance());
        record.setDescription(rel.description());
        record.setLastUpdatedBy(rel.lastUpdatedBy());
        record.setLastUpdatedAt(Timestamp.valueOf(rel.lastUpdatedAt()));
        return record;
    };


    private final DSLContext dsl;


    @Autowired
    public EntityRelationshipDao(DSLContext dsl) {
        checkNotNull(dsl, "dsl cannot be null");
        this.dsl = dsl;
    }


    public Collection<EntityRelationship> findForGenericEntitySelector(GenericSelector selector) {
        Condition anyMatch = mkGenericSelectorCondition(selector);
        return doQuery(anyMatch);
    }


    public Collection<EntityRelationship> findRelationshipsInvolving(EntityReference ref) {
        checkNotNull(ref, "ref cannot be null");
        return doQuery(mkExactRefMatchCondition(ref));
    }


    public Map<EntityKind, Integer> tallyRelationshipsInvolving(EntityReference ref) {
        checkNotNull(ref, "ref cannot be null");

        return dsl
                .select(ENTITY_RELATIONSHIP.KIND_B, DSL.count())
                .from(ENTITY_RELATIONSHIP)
                .where(ENTITY_RELATIONSHIP.KIND_A.eq(ref.kind().name()))
                .and(ENTITY_RELATIONSHIP.ID_A.eq(ref.id()))
                .groupBy(ENTITY_RELATIONSHIP.KIND_B)
                .unionAll(DSL.select(ENTITY_RELATIONSHIP.KIND_A, DSL.count())
                        .from(ENTITY_RELATIONSHIP)
                        .where(ENTITY_RELATIONSHIP.KIND_B.eq(ref.kind().name()))
                        .and(ENTITY_RELATIONSHIP.ID_B.eq(ref.id()))
                        .groupBy(ENTITY_RELATIONSHIP.KIND_A))
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        r -> EntityKind.valueOf(r.get(0, String.class)),
                        r -> r.get(1, Integer.class),
                        Integer::sum));
    }


    public EntityRelationship getById(Long id){
        return dsl
                .select(ENTITY_RELATIONSHIP.fields())
                .select(NAME_A, NAME_B)
                .select(EXT_ID_A, EXT_ID_B)
                .from(ENTITY_RELATIONSHIP)
                .where(ENTITY_RELATIONSHIP.ID.eq(id))
                .fetchOne(TO_DOMAIN_MAPPER);
    }


    @Deprecated
    public int save(EntityRelationship entityRelationship) {
        checkNotNull(entityRelationship, "entityRelationship cannot be null");

        return ! exists(entityRelationship.toKey())
                ? dsl.executeInsert(TO_RECORD_MAPPER.apply(entityRelationship))
                : 0;
    }


    public boolean create(EntityRelationship relationship) {
        return dsl.executeInsert(TO_RECORD_MAPPER.apply(relationship)) == 1;
    }


    public boolean remove(EntityRelationshipKey key) {
        checkNotNull(key, "key cannot be null");

        return dsl
                .deleteFrom(ENTITY_RELATIONSHIP)
                .where(mkExactKeyMatchCondition(key))
                .execute() == 1;
    }


    public boolean update(EntityRelationshipKey key,
                          UpdateEntityRelationshipParams params,
                          String username) {
        return dsl
                .update(ENTITY_RELATIONSHIP)
                .set(ENTITY_RELATIONSHIP.RELATIONSHIP, params.relationshipKind())
                .set(ENTITY_RELATIONSHIP.DESCRIPTION, params.description())
                .set(ENTITY_RELATIONSHIP.LAST_UPDATED_BY, username)
                .set(ENTITY_RELATIONSHIP.LAST_UPDATED_AT, DateTimeUtilities.nowUtcTimestamp())
                .where(mkExactKeyMatchCondition(key))
                .execute() == 1;

    }


    public int deleteForGenericEntitySelector(GenericSelector selector) {
        return doDelete(mkGenericSelectorCondition(selector));
    }


    public int removeAnyInvolving(EntityReference entityReference) {
        return doDelete(mkExactRefMatchCondition(entityReference));
    }


    // --- HELPERS ---


    private int doDelete(Condition condition) {
        return dsl
                .deleteFrom(ENTITY_RELATIONSHIP)
                .where(condition)
                .execute();
    }


    private Collection<EntityRelationship> doQuery(Condition condition) {
        return dsl
                .select(ENTITY_RELATIONSHIP.fields())
                .select(NAME_A, NAME_B)
                .select(EXT_ID_A, EXT_ID_B)
                .from(ENTITY_RELATIONSHIP)
                .where(condition)
                .fetch(TO_DOMAIN_MAPPER);
    }


    private boolean exists(EntityRelationshipKey key) {

        return dsl
                .fetchExists(
                    DSL.select()
                       .from(ENTITY_RELATIONSHIP)
                       .where(mkExactKeyMatchCondition(key)));
        }


    private Condition mkExactRefMatchCondition(EntityReference ref) {
        Condition matchesA = ENTITY_RELATIONSHIP.ID_A.eq(ref.id()).and(ENTITY_RELATIONSHIP.KIND_A.eq(ref.kind().name()));
        Condition matchesB = ENTITY_RELATIONSHIP.ID_B.eq(ref.id()).and(ENTITY_RELATIONSHIP.KIND_B.eq(ref.kind().name()));
        return matchesA.or(matchesB);
    }

    public Collection<EntityRelationship> getEntityRelationshipsByKind(org.finos.waltz.model.rel.RelationshipKind relationshipKind) {
        return dsl
                .select(ENTITY_RELATIONSHIP.fields())
                .from(ENTITY_RELATIONSHIP)
                .where(ENTITY_RELATIONSHIP.RELATIONSHIP.eq(relationshipKind.code()))
                .fetch(r -> {
                    EntityRelationshipRecord record = r.into(ENTITY_RELATIONSHIP);
                    return ImmutableEntityRelationship.builder()
                            .id(record.getId())
                            .a(ImmutableEntityReference.builder()
                                    .kind(EntityKind.valueOf(record.getKindA()))
                                    .id(record.getIdA())
                                    .build())
                            .b(ImmutableEntityReference.builder()
                                    .kind(EntityKind.valueOf(record.getKindB()))
                                    .id(record.getIdB())
                                    .build())
                            .provenance(record.getProvenance())
                            .relationship(record.getRelationship())
                            .description(record.getDescription())
                            .lastUpdatedBy(record.getLastUpdatedBy())
                            .lastUpdatedAt(toLocalDateTime(record.getLastUpdatedAt()))
                            .build();
                });
    }


    private Condition mkExactKeyMatchCondition(EntityRelationshipKey key) {
        return ENTITY_RELATIONSHIP.ID_A.eq(key.a().id())
                .and(ENTITY_RELATIONSHIP.KIND_A.eq(key.a().kind().name()))
                .and(ENTITY_RELATIONSHIP.ID_B.eq(key.b().id())
                        .and(ENTITY_RELATIONSHIP.KIND_B.eq(key.b().kind().name())))
                .and(ENTITY_RELATIONSHIP.RELATIONSHIP.eq(key.relationshipKind()));
    }


    private Condition mkGenericSelectorCondition(GenericSelector selector) {
        Condition matchesA = ENTITY_RELATIONSHIP.ID_A.in(selector.selector())
                .and(ENTITY_RELATIONSHIP.KIND_A.eq(selector.kind().name()));

        Condition matchesB = ENTITY_RELATIONSHIP.ID_B.in(selector.selector())
                .and(ENTITY_RELATIONSHIP.KIND_B.eq(selector.kind().name()));

        return matchesA.or(matchesB);
    }

    public int[] saveAll(String userId, long groupId, List<Long> changeInitiativeIds) {
        checkNotNull(userId, "userId cannot be null");
        checkNotNull(groupId, "groupId cannot be null");
        checkNotNull(changeInitiativeIds, "changeInitiativeIds cannot be null");
        
        Query[] queries  = changeInitiativeIds
                .stream()
                .map(ci -> DSL
                        .insertInto(ENTITY_RELATIONSHIP)
                        .set(ENTITY_RELATIONSHIP.KIND_A, EntityKind.APP_GROUP.name())
                        .set(ENTITY_RELATIONSHIP.ID_A, groupId)
                        .set(ENTITY_RELATIONSHIP.KIND_B, EntityKind.CHANGE_INITIATIVE.name())
                        .set(ENTITY_RELATIONSHIP.ID_B, ci)
                        .set(ENTITY_RELATIONSHIP.RELATIONSHIP, RelationshipKind.RELATES_TO.name())
                        .set(ENTITY_RELATIONSHIP.PROVENANCE, "waltz")
                        .set(ENTITY_RELATIONSHIP.LAST_UPDATED_AT, DateTimeUtilities.nowUtcTimestamp())
                        .set(ENTITY_RELATIONSHIP.LAST_UPDATED_BY, "admin")
                        .onDuplicateKeyUpdate()
                        .set(ENTITY_RELATIONSHIP.ID_B, ci)
                        .set(ENTITY_RELATIONSHIP.LAST_UPDATED_AT, DateTimeUtilities.nowUtcTimestamp()))
                .toArray(Query[]::new);
        return dsl.batch(queries).execute();
    }

    public int removeAll(long groupId, List<Long> changeInitiativeIds) {
        checkNotNull(groupId, "groupId cannot be null");
        checkNotNull(changeInitiativeIds, "changeInitiativeIds cannot be null");

        return dsl
                .delete(ENTITY_RELATIONSHIP)
                .where(ENTITY_RELATIONSHIP.ID_A.eq(groupId))
                .and(ENTITY_RELATIONSHIP.ID_B.in(changeInitiativeIds))
                .execute();
    }


    public void migrateEntityRelationships(EntityReference sourceReference, EntityReference targetReference, String userId) {

        dsl.transaction(ctx -> {

            DSLContext tx = ctx.dsl();

            LOG.info("Migrating entity relationships from source: {}/{} to target: {}/{}",
                    sourceReference.kind().prettyName(),
                    sourceReference.id(),
                    targetReference.kind().prettyName(),
                    targetReference.id());

            Condition measurableIsA = Tables.ENTITY_RELATIONSHIP.ID_A.eq(sourceReference.id()).and(Tables.ENTITY_RELATIONSHIP.KIND_A.eq(sourceReference.kind().name()));
            Condition measurableIsB = Tables.ENTITY_RELATIONSHIP.ID_B.eq(sourceReference.id()).and(Tables.ENTITY_RELATIONSHIP.KIND_B.eq(sourceReference.kind().name()));

            SelectOrderByStep<Record3<Long, String, String>> allowedKindAUpdates = selectKindARelationshipsThatCanBeAdded(sourceReference, targetReference);

            int kindARelsUpdated = tx
                    .update(Tables.ENTITY_RELATIONSHIP)
                    .set(Tables.ENTITY_RELATIONSHIP.ID_A, targetReference.id())
                    .set(Tables.ENTITY_RELATIONSHIP.LAST_UPDATED_AT, DateTimeUtilities.nowUtcTimestamp())
                    .set(Tables.ENTITY_RELATIONSHIP.LAST_UPDATED_BY, userId)
                    .from(allowedKindAUpdates)
                    .where(measurableIsA)
                    .and(Tables.ENTITY_RELATIONSHIP.KIND_B.eq(allowedKindAUpdates.field(Tables.ENTITY_RELATIONSHIP.KIND_B))
                            .and(Tables.ENTITY_RELATIONSHIP.ID_B.eq(allowedKindAUpdates.field(Tables.ENTITY_RELATIONSHIP.ID_B))
                                    .and(Tables.ENTITY_RELATIONSHIP.RELATIONSHIP.eq(allowedKindAUpdates.field(Tables.ENTITY_RELATIONSHIP.RELATIONSHIP)))))
                    .execute();

            SelectOrderByStep<Record3<Long, String, String>> allowedKindBUpdates = selectKindBRelationshipsThatCanBeAdded(sourceReference, targetReference);

            int kindBRelsUpdated = tx
                    .update(Tables.ENTITY_RELATIONSHIP)
                    .set(Tables.ENTITY_RELATIONSHIP.ID_B, targetReference.id())
                    .set(Tables.ENTITY_RELATIONSHIP.LAST_UPDATED_AT, DateTimeUtilities.nowUtcTimestamp())
                    .set(Tables.ENTITY_RELATIONSHIP.LAST_UPDATED_BY, userId)
                    .from(allowedKindBUpdates)
                    .where(measurableIsB)
                    .and(Tables.ENTITY_RELATIONSHIP.KIND_A.eq(allowedKindBUpdates.field(Tables.ENTITY_RELATIONSHIP.KIND_A))
                            .and(Tables.ENTITY_RELATIONSHIP.ID_A.eq(allowedKindBUpdates.field(Tables.ENTITY_RELATIONSHIP.ID_A))
                                    .and(Tables.ENTITY_RELATIONSHIP.RELATIONSHIP.eq(allowedKindBUpdates.field(Tables.ENTITY_RELATIONSHIP.RELATIONSHIP)))))
                    .execute();

            int entityRelsRemoved = tx
                    .deleteFrom(Tables.ENTITY_RELATIONSHIP)
                    .where(measurableIsA.or(measurableIsB))
                    .execute();

            LOG.info("Migrated {} relationships from source: {}/{} to target: {}/{}",
                    kindARelsUpdated + kindBRelsUpdated,
                    sourceReference.kind().prettyName(),
                    sourceReference.id(),
                    targetReference.kind().prettyName(),
                    targetReference.id());

            LOG.info("Removed {} relationships that could not be migrated from source: {}/{} to target: {}/{} as a relationship already exists",
                    entityRelsRemoved,
                    sourceReference.kind().prettyName(),
                    sourceReference.id(),
                    targetReference.kind().prettyName(),
                    targetReference.id());
        });
    }

    private SelectOrderByStep<Record3<Long, String, String>> selectKindARelationshipsThatCanBeAdded(EntityReference source, EntityReference target) {

        SelectConditionStep<Record3<Long, String, String>> targets = DSL
                .select(Tables.ENTITY_RELATIONSHIP.ID_B, Tables.ENTITY_RELATIONSHIP.KIND_B, Tables.ENTITY_RELATIONSHIP.RELATIONSHIP)
                .from(Tables.ENTITY_RELATIONSHIP)
                .where(Tables.ENTITY_RELATIONSHIP.ID_A.eq(target.id())
                        .and(Tables.ENTITY_RELATIONSHIP.KIND_A.eq(target.kind().name())));

        SelectConditionStep<Record3<Long, String, String>> migrations = DSL
                .select(Tables.ENTITY_RELATIONSHIP.ID_B, Tables.ENTITY_RELATIONSHIP.KIND_B, Tables.ENTITY_RELATIONSHIP.RELATIONSHIP)
                .from(Tables.ENTITY_RELATIONSHIP)
                .where(Tables.ENTITY_RELATIONSHIP.ID_A.eq(source.id())
                        .and(Tables.ENTITY_RELATIONSHIP.KIND_A.eq(source.kind().name())));

        return migrations.except(targets);
    }

    private SelectOrderByStep<Record3<Long, String, String>> selectKindBRelationshipsThatCanBeAdded(EntityReference source, EntityReference target) {

        SelectConditionStep<Record3<Long, String, String>> targets = DSL
                .select(Tables.ENTITY_RELATIONSHIP.ID_A, Tables.ENTITY_RELATIONSHIP.KIND_A, Tables.ENTITY_RELATIONSHIP.RELATIONSHIP)
                .from(Tables.ENTITY_RELATIONSHIP)
                .where(Tables.ENTITY_RELATIONSHIP.ID_B.eq(target.id())
                        .and(Tables.ENTITY_RELATIONSHIP.KIND_B.eq(target.kind().name())));

        SelectConditionStep<Record3<Long, String, String>> migrations = DSL
                .select(Tables.ENTITY_RELATIONSHIP.ID_A, Tables.ENTITY_RELATIONSHIP.KIND_A, Tables.ENTITY_RELATIONSHIP.RELATIONSHIP)
                .from(Tables.ENTITY_RELATIONSHIP)
                .where(Tables.ENTITY_RELATIONSHIP.ID_B.eq(source.id())
                        .and(Tables.ENTITY_RELATIONSHIP.KIND_B.eq(source.kind().name())));

        return migrations.except(targets);
    }
}
