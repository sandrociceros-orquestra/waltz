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

import org.finos.waltz.common.MapUtilities;
import org.finos.waltz.common.SetUtilities;
import org.finos.waltz.common.StringUtilities;
import org.finos.waltz.model.CommonTableFields;
import org.finos.waltz.model.EndOfLifeStatus;
import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.EntityReference;
import org.finos.waltz.model.ImmutableEntityReference;
import org.finos.waltz.model.tally.ImmutableOrderedTally;
import org.finos.waltz.model.tally.ImmutableTally;
import org.finos.waltz.model.tally.OrderedTally;
import org.finos.waltz.model.tally.Tally;
import org.finos.waltz.schema.tables.records.ChangeLogRecord;
import org.jooq.Batch;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.RecordMapper;
import org.jooq.SQL;
import org.jooq.SQLDialect;
import org.jooq.Select;
import org.jooq.SelectHavingStep;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableRecord;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.finos.waltz.common.Checks.checkNotNull;
import static org.finos.waltz.common.DateTimeUtilities.toLocalDate;
import static org.finos.waltz.common.DateTimeUtilities.toSqlDate;
import static org.finos.waltz.common.SetUtilities.asSet;
import static org.finos.waltz.common.SetUtilities.minus;
import static org.finos.waltz.model.EntityReference.mkRef;
import static org.jooq.impl.DSL.currentDate;
import static org.jooq.impl.DSL.inline;

public class JooqUtilities {

    public static final Field<Integer> TALLY_COUNT_FIELD = DSL.field("count", Integer.class);


    public static Optional<EntityReference> maybeReadRef(Record record,
                                                         Field<String> kindField,
                                                         Field<Long> idField) {
        return ofNullable(record.getValue(kindField))
                .map(kindStr -> mkRef(
                        EntityKind.valueOf(kindStr),
                        record.getValue(idField)));
    }

    public static Optional<EntityReference> maybeReadRef(Record record,
                                                         Field<String> kindField,
                                                         Field<Long> idField,
                                                         Field<String> nameField) {
        return ofNullable(record.getValue(kindField))
                .map(kindStr -> mkRef(
                        EntityKind.valueOf(kindStr),
                        record.getValue(idField),
                        record.getValue(nameField)));
    }

    public static EntityReference readRef(Record record, Field<String> kindField, Field<Long> idField) {
        return mkRef(
                EntityKind.valueOf(record.getValue(kindField)),
                record.getValue(idField));
    }


    public static EntityReference readRef(Record record, Field<String> kindField, Field<Long> idField, Field<String> nameField) {
        return mkRef(
                EntityKind.valueOf(record.getValue(kindField)),
                record.getValue(idField),
                record.getValue(nameField));
    }


    public static <T extends TableRecord<?>> Collector<T, Set<T>, Batch> batchInsertCollector(DSLContext dsl) {
        return batchOperationCollector(dsl, dsl::batchInsert);
    }


    public static <T extends UpdatableRecord<?>> Collector<T, Set<T>, Batch> batchStoreCollector(DSLContext dsl) {
        return batchOperationCollector(dsl, dsl::batchStore);
    }


    private static <T extends TableRecord<?>> Collector<T, Set<T>, Batch> batchOperationCollector(DSLContext dsl,
                                                                                                  Function<Set<T>, Batch> operation) {
        return Collector.of(
                HashSet::new,
                Set::add,
                SetUtilities::union,
                operation,
                Collector.Characteristics.CONCURRENT);
    }


    /**
     * Expects result set like: { Id, Count }
     */
    public static final RecordMapper<Record2<String,Integer>, Tally<String>> TO_STRING_TALLY = r ->
            ImmutableTally.<String>builder()
                    .count(r.value2())
                    .id(r.value1())
                    .build();

    public static final RecordMapper<Record3<String, Integer, Integer>, OrderedTally<String>> TO_ORDERED_STRING_TALLY = r ->
            ImmutableOrderedTally.<String>builder()
                    .count(r.value2())
                    .id(r.value1())
                    .index(r.value3())
                    .build();


    public static final RecordMapper<Record2<Long,Integer>, Tally<Long>> TO_LONG_TALLY = r ->
            ImmutableTally.<Long>builder()
                    .count(r.value2())
                    .id(r.value1())
                    .build();


    /**
     * <ul>
     *     <li>value1 : id</li>
     *     <li>value2 : name</li>
     *     <li>value3 : kind</li>
     * </ul>
     */
    public static final RecordMapper<? super Record3<Long, String, String>, EntityReference> TO_ENTITY_REFERENCE = r ->
        ImmutableEntityReference.builder()
                .id(r.value1())
                .name(r.value2())
                .kind(EntityKind.valueOf(r.value3()))
                .build();


    public static List<Tally<String>> calculateStringTallies(
            DSLContext dsl,
            Table table,
            Field<String> fieldToTally,
            Condition recordsInScopeCondition) {

        Select<Record2<String, Integer>> tallyQuery = makeTallyQuery(
                dsl,
                table,
                fieldToTally,
                recordsInScopeCondition);

        return tallyQuery
                .fetch(TO_STRING_TALLY);
    }


    public static List<Tally<String>> calculateStringTallies(
            Collection<? extends Record> records,
            Field<String> fieldToTally) {

        return records.stream()
                .collect(groupingBy(r -> r.getValue(fieldToTally), counting()))
                .entrySet()
                .stream()
                .map(e -> ImmutableTally.<String>builder()
                        .id(e.getKey())
                        .count(e.getValue())
                        .build())
                .collect(toList());
    }


    public static List<Tally<Long>> calculateLongTallies(
            DSLContext dsl,
            Table table,
            Field<Long> fieldToTally,
            Condition recordsInScopeCondition) {
        SelectHavingStep<Record2<Long, Integer>> query = makeTallyQuery(
                dsl,
                table,
                fieldToTally,
                recordsInScopeCondition);
        return query.fetch(TO_LONG_TALLY);
    }



    /**
     * Attempts to read the value of the field (f)
     * from the record (r).  If the value is null
     * then the default (dflt) values is returned.
     * @param r Record
     * @param f Field to read
     * @param dflt  Default value to return if field-value is null
     * @param <T>  Type of the field-value
     * @return
     */
    public static <T> T safeGet(Record r, Field<T> f, T dflt) {
        return ofNullable(r.getValue(f))
                .orElse(dflt);
    }


    public static boolean isPostgres(SQLDialect dialect) {
        return dialect == SQLDialect.POSTGRES;
    }


    public static boolean isMariaDB(SQLDialect dialect) {
        return dialect == SQLDialect.MARIADB;
    }


    public static boolean isSQLServer(SQLDialect dialect) {
        // cannot do direct comparison to enum as may not be present (i.e. in community edition)
        return dialect.name().startsWith("SQLSERVER");
    }


    public static class MSSQL {

        public static SQL mkContains(Collection<String> terms) {
            checkNotNull(terms, "terms cannot be null");
            return mkContains(terms.toArray(new String[0]));
        }

        public static SQL mkContains(String... terms) {
            StringJoiner joiner = new StringJoiner(" AND ", "CONTAINS(*, '", "')");
            Stream.of(terms)
                    .filter(StringUtilities::notEmpty)
                    .map(t -> wrapSpecialInQuotes(t))
                    .forEach(joiner::add);
            return DSL.sql(joiner.toString());
        }

        public static SQL mkContainsPrefix(Collection<String> terms) {
            checkNotNull(terms, "terms cannot be null");

            // this is based on: https://stackoverflow.com/a/4321828
            List<String> wildcardTerms = terms
                    .stream()
                    .map(s -> "\"" + s + "*\"")
                    .map(s -> s.replace('&', ' '))
                    .collect(Collectors.toList());

            return mkContains(wildcardTerms);
        }

        private static String wrapSpecialInQuotes(String t) {
            return t.contains("&") ? "\"" + t + "\"" : t;
        }
    }


    public static <T> SelectHavingStep<Record2<T, Integer>> makeTallyQuery(
            DSLContext dsl,
            Table table,
            Field<T> fieldToTally,
            Condition recordsInScopeCondition) {
        return dsl.select(
                fieldToTally,
                DSL.count(fieldToTally).as(TALLY_COUNT_FIELD))
                .from(table)
                .where(dsl.renderInlined(recordsInScopeCondition))
                .groupBy(fieldToTally);
    }


    public static <T> SelectHavingStep<Record3<T, Integer, Integer>> makeOrderedTallyQuery(
            DSLContext dsl,
            Table table,
            Field<T> fieldToTally,
            Condition recordsInScopeCondition) {
        return dsl.select(
                fieldToTally,
                DSL.count(fieldToTally).as(TALLY_COUNT_FIELD),
                DSL.rowNumber().over(DSL.orderBy(DSL.count(fieldToTally).desc())))
                .from(table)
                .where(dsl.renderInlined(recordsInScopeCondition))
                .groupBy(fieldToTally);
    }


    public static Field<String> mkEndOfLifeStatusDerivedField(Field<Date> endOfLifeDateField) {

        return DSL
                .when(endOfLifeDateField.lt(currentDate()), inline(EndOfLifeStatus.END_OF_LIFE.name()))
                .otherwise(inline(EndOfLifeStatus.NOT_END_OF_LIFE.name()));
    }


    public static Condition mkBasicTermSearch(Field<String> field,
                                               List<String> terms) {
        return terms
                .stream()
                .map(field::containsIgnoreCase)
                .reduce(DSL.trueCondition(), Condition::and);
    }


    public static Condition mkStartsWithTermSearch(Field<String> field,
                                                   List<String> terms) {
        return terms
                .stream()
                .map(field::startsWithIgnoreCase)
                .reduce(DSL.falseCondition(), Condition::or);
    }


    public static Condition mkDateRangeCondition(TableField<ChangeLogRecord, Timestamp> field, java.sql.Date startDate, java.sql.Date endDate) {
        long time = startDate.getTime();
        Timestamp startOfStartDay = new Timestamp(time);

        long startOfEndDate = endDate.getTime();
        Timestamp startOfEndDay = new Timestamp(startOfEndDate);

        long nextDay = getOneDayLater(startOfEndDay).getTime();
        Timestamp startOfDayAfterEndDay = new Timestamp(nextDay);

        return field.ge(startOfStartDay).and(field.lt(startOfDayAfterEndDay));
    }


    public static Condition mkDateRangeCondition(TableField<ChangeLogRecord, Timestamp> field, java.sql.Date date) {
        return mkDateRangeCondition(field, date, date);
    }


    public static Condition mkDateRangeCondition(TableField<ChangeLogRecord, Timestamp> field, java.util.Date date) {
        long time = date.getTime();
        Timestamp startOfDay = new Timestamp(time);

        long timeAfterDay = getOneDayLater(startOfDay).getTime();
        Timestamp endOfDay = new Timestamp(timeAfterDay);

        return field.ge(startOfDay).and(field.lt(endOfDay));
    }


    private static Date getOneDayLater(Timestamp timestamp) {
        return toSqlDate(toLocalDate(timestamp).plusDays(1));
    }


    public static CommonTableExpression<Record1<Long>> selectorToCTE(String name,
                                                                     GenericSelector genericSelector) {
        return DSL
                .name(name)
                .fields("id")
                .as(genericSelector.selector());
    }


    public static Collection<? extends Field<?>> fieldsWithout(Table<?> t, Field<?>... excludes) {
        return minus(
                asSet(t.fields()),
                asSet(excludes));
    }


    public static int summarizeResults(int[] rcs) {
        return IntStream.of(rcs).sum();
    }


    public static CommonTableFields<?> determineCommonTableFields(EntityKind kind) {
        return determineCommonTableFields(kind, null);
    }


    public static CommonTableFields<?> determineCommonTableFields(EntityKind kind, String alias) {
        return CommonTableFieldsRegistry.determineCommonTableFields(kind, alias);
    }


    /**
     * Given an entity kind and a qualifier kind, this will create a map allowing you to lookup references based on the
     * external id's of all associated (active) entities which are also aligned to the qualifier reference.
     * Empty external id's are skipped.
     *
     * @param dsl  connection to Waltz database
     * @param entityKind primary entity type to return
     * @param qualifierReference  used to restrict the primary entities
     * @return  Map of external id to entity references
     */
    public static Map<String, EntityReference> loadExternalIdToEntityRefMap(DSLContext dsl,
                                                                            EntityKind entityKind,
                                                                            EntityReference qualifierReference) {
        CommonTableFields<?> ctf = determineCommonTableFields(entityKind);

        Condition qualifierCondition = qualifierReference == null || ctf.qualifierKindField() == null || ctf.qualifierIdField() == null
            ? DSL.trueCondition()
            : ctf.qualifierKindField().eq(qualifierReference.kind().name())
                .and(ctf.qualifierIdField().eq(qualifierReference.id()));

        return loadExternalIdToEntityRefMap(dsl, ctf, qualifierCondition);

    }


    /**
     * Given an entity kind and a qualifier kind, this will create a map allowing you to lookup references based on the
     * external id's of all associated (active) entities which are also aligned to the qualifier kind.
     * Empty external id's are skipped.
     *
     * @param dsl  connection to Waltz database
     * @param entityKind primary entity type to return
     * @param qualifierKind  used to restrict the primary entities
     * @return  Map of external id to entity references
     */
    public static Map<String, EntityReference> loadExternalIdToEntityRefMap(DSLContext dsl,
                                                                            EntityKind entityKind,
                                                                            EntityKind qualifierKind) {
        CommonTableFields<?> ctf = determineCommonTableFields(entityKind);

        Condition qualifierCondition = qualifierKind == null || ctf.qualifierKindField() == null
            ? DSL.trueCondition()
            : ctf.qualifierKindField().eq(qualifierKind.name());

        return loadExternalIdToEntityRefMap(dsl, ctf, qualifierCondition);
    }


    /**
     * Given an entity kind, this will create a map allowing you to lookup references based on the external id's
     * of all associated (active) entities.  Empty external id's are skipped.
     *
     * @param dsl  connection to Waltz database
     * @param entityKind
     * @return  Map of external id to entity references
     */
    public static Map<String, EntityReference> loadExternalIdToEntityRefMap(DSLContext dsl,
                                                                            EntityKind entityKind) {
        CommonTableFields<?> ctf = determineCommonTableFields(entityKind);
        return loadExternalIdToEntityRefMap(dsl, ctf, DSL.trueCondition());
    }


    private static Map<String, EntityReference> loadExternalIdToEntityRefMap(DSLContext dsl,
                                                                             CommonTableFields<?> ctf,
                                                                             Condition qualifierCondition) {
        Set<EntityReference> results = dsl
                .select(ctf.externalIdField(),
                        ctf.idField(),
                        ctf.nameField(),
                        ctf.descriptionField())
                .from(ctf.table())
                .where(ctf.isActiveCondition())
                .and(ctf.externalIdField().isNotNull())
                .and(qualifierCondition)
                .fetch()
                .stream()
                .map(r -> mkRef(
                        ctf.entityKind(),
                        r.get(ctf.idField()),
                        r.get(ctf.nameField()),
                        r.get(ctf.descriptionField()),
                        r.get(ctf.externalIdField())))
                .filter(d -> d.externalId().isPresent())
                .collect(Collectors.toSet());

        return MapUtilities.indexBy(
                results,
                d -> d.externalId().get());
    }
}
