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

package org.finos.waltz.service.report_grid;

import org.finos.waltz.common.SetUtilities;
import org.finos.waltz.data.report_grid.ReportGridDao;
import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.EntityReference;
import org.finos.waltz.model.EntityReferenceUtilities;
import org.finos.waltz.model.IdSelectionOptions;
import org.finos.waltz.model.NameProvider;
import org.finos.waltz.model.entity_named_note.EntityNamedNote;
import org.finos.waltz.model.rating.RatingSchemeItem;
import org.finos.waltz.model.report_grid.CellOption;
import org.finos.waltz.model.report_grid.FilterOperator;
import org.finos.waltz.model.report_grid.GridFilter;
import org.finos.waltz.model.report_grid.ImmutableReportGridFilterInfo;
import org.finos.waltz.model.report_grid.ReportGridCell;
import org.finos.waltz.model.report_grid.ReportGridDefinition;
import org.finos.waltz.model.report_grid.ReportGridFilterInfo;
import org.finos.waltz.model.report_grid.ReportGridInstance;
import org.finos.waltz.model.report_grid.ReportSubject;
import org.finos.waltz.model.utils.IdUtilities;
import org.finos.waltz.service.app_group.AppGroupService;
import org.finos.waltz.service.entity_named_note.EntityNamedNoteService;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Collections.emptySet;
import static org.finos.waltz.common.Checks.checkNotNull;
import static org.finos.waltz.common.CollectionUtilities.first;
import static org.finos.waltz.common.CollectionUtilities.isEmpty;
import static org.finos.waltz.common.CollectionUtilities.notEmpty;
import static org.finos.waltz.common.ListUtilities.map;
import static org.finos.waltz.common.MapUtilities.groupBy;
import static org.finos.waltz.common.MapUtilities.indexBy;
import static org.finos.waltz.common.SetUtilities.asSet;
import static org.finos.waltz.common.SetUtilities.intersection;
import static org.finos.waltz.common.SetUtilities.minus;
import static org.finos.waltz.common.SetUtilities.union;
import static org.finos.waltz.common.StringUtilities.notEmpty;
import static org.finos.waltz.model.EntityReference.mkRef;
import static org.finos.waltz.model.IdSelectionOptions.mkOpts;
import static org.finos.waltz.service.report_grid.ReportGridUtilities.parseGridFilterNoteText;
import static org.finos.waltz.service.report_grid.ReportGridUtilities.parseGridFilters;
import static org.jooq.lambda.tuple.Tuple.tuple;

@Service
public class ReportGridFilterViewService {

    private static final Logger LOG = LoggerFactory.getLogger(ReportGridFilterViewService.class);

    private static final String REPORT_GRID_APP_GROUP_CREATION_NOTE_TYPE_EXT_ID = "WALTZ_REPORT_GRID_FILTER_PRESET";
    private static final String NOT_PROVIDED_OPTION_CODE = "NOT_PROVIDED";

    private final ReportGridDao reportGridDao;
    private final ReportGridService reportGridService;
    private final EntityNamedNoteService entityNamedNoteService;
    private final AppGroupService appGroupService;


    @Autowired
    public ReportGridFilterViewService(ReportGridDao reportGridDao,
                                       ReportGridService reportGridService,
                                       EntityNamedNoteService entityNamedNoteService,
                                       AppGroupService appGroupService) {

        checkNotNull(reportGridDao, "reportGridDao cannot be null");
        checkNotNull(entityNamedNoteService, "entityNamedNoteService cannot be null");
        checkNotNull(appGroupService, "appGroupService cannot be null");
        checkNotNull(entityNamedNoteService, "entityNamedNoteService cannot be null");

        this.entityNamedNoteService = entityNamedNoteService;
        this.reportGridService = reportGridService;
        this.reportGridDao = reportGridDao;
        this.appGroupService = appGroupService;
    }


    public int recalculateAppGroupFromNoteText(Long appGroupId, String userId) {

        Set<EntityNamedNote> filterNotesForGroup = entityNamedNoteService.findByNoteTypeExtIdAndEntityReference(
                REPORT_GRID_APP_GROUP_CREATION_NOTE_TYPE_EXT_ID,
                mkRef(EntityKind.APP_GROUP, appGroupId));

        EntityNamedNote note = checkOnlyOneNoteExistsAndGetIt(appGroupId, filterNotesForGroup);

        ReportGridFilterInfo gridFilterInfo = parseGridFilterInfo(
                appGroupId,
                note.noteText());

        if (gridFilterInfo == null) {
            throw new IllegalArgumentException("Cannot parse filter grid info from note text");
        } else {
            Tuple3<EntityKind, Long, Set<EntityReference>> appGroupIdToEntries = determineApplicationsInGroup(gridFilterInfo);
            appGroupService.synchGroupEntries(asSet(appGroupIdToEntries), userId);
            return appGroupIdToEntries.v3.size();
        }
    }


    private EntityNamedNote checkOnlyOneNoteExistsAndGetIt(Long appGroupId,
                                                           Set<EntityNamedNote> filterNotesForGroup) {
        if (isEmpty(filterNotesForGroup)) {
            throw new IllegalArgumentException(format(
                    "Cannot find Report Grid Filter Preset note for application group: %d",
                    appGroupId));
        }

        if (filterNotesForGroup.size() > 1) {
            throw new IllegalArgumentException("Cannot have more than one Report Grid Filter note per application group");
        }

        return first(filterNotesForGroup);
    }


    public void generateAppGroupsFromFilter() {
        LOG.info("Starting filter group population");

        LOG.info("Loading filter info from notes");
        Set<ReportGridFilterInfo> gridInfoWithFilters = findGridInfoWithFilters();

        Set<Tuple3<EntityKind, Long, Set<EntityReference>>> appGroupToEntries = determineAppGroupEntries(gridInfoWithFilters);

        LOG.info("Populating application groups from filters");
        appGroupService.synchGroupEntries(appGroupToEntries, "admin");

        LOG.info("Finished updating filter groups");
    }


    private Set<Tuple3<EntityKind, Long, Set<EntityReference>>> determineAppGroupEntries(Set<ReportGridFilterInfo> gridInfoWithFilters) {
        return gridInfoWithFilters
                .stream()
                .map(this::determineApplicationsInGroup)
                .collect(Collectors.toSet());
    }


    /**
     *
     * @param reportGridFilterInfo group ref, filters etc
     * @return tuple{subjectKind, appGroupId, [entries]}
     */
    private Tuple3<EntityKind, Long, Set<EntityReference>> determineApplicationsInGroup(ReportGridFilterInfo reportGridFilterInfo) {
        return reportGridFilterInfo
                .gridDefinition()
                .id()
                .flatMap(id -> reportGridService.getByIdAndSelectionOptions(
                    id,
                    reportGridFilterInfo.idSelectionOptions(),
                    "filter-note-user"))
                .map(grid -> {
                    ReportGridInstance instance = grid.instance();
                    Set<ReportGridCell> cellData = instance.cellData();

                    Set<EntityReference> subjectRefs = SetUtilities.map(
                            instance.subjects(),
                            ReportSubject::entityReference);

                    Set<EntityReference> subjectsPassingFilters = applyFilters(
                            cellData,
                            reportGridFilterInfo.gridFilters(),
                            subjectRefs,
                            instance.ratingSchemeItems());

                    return tuple(
                            reportGridFilterInfo.gridDefinition().subjectKind(),
                            reportGridFilterInfo.appGroupId(),
                            subjectsPassingFilters);
                })
                .orElseThrow(() -> new IllegalStateException("Cannot create grid instance with params" + reportGridFilterInfo));
    }


    private Set<EntityReference> applyFilters(Set<ReportGridCell> cellData,
                                              Set<GridFilter> gridFilters,
                                              Set<EntityReference> subjectRefs,
                                              Set<RatingSchemeItem> ratingSchemeItems) {

        if (isEmpty(gridFilters)) {
            //If there are no filters all the apps should populate the group
            return subjectRefs;
        } else {
            Map<Long, RatingSchemeItem> ratingSchemeItemById = IdUtilities.indexById(ratingSchemeItems);
            Map<Long, EntityReference> subjectsById = EntityReferenceUtilities.indexById(subjectRefs);

            Map<Long, Collection<ReportGridCell>> dataByCol = groupBy(cellData, ReportGridCell::columnDefinitionId);

            Set<Set<Long>> appIdsPassingFilters = gridFilters
                    .stream()
                    .map(filter -> {
                        Collection<ReportGridCell> cellDataForColumn = dataByCol.getOrDefault(filter.columnDefinitionId(), emptySet());

                        if (filter.filterOperator().equals(FilterOperator.CONTAINS_ANY_OPTION)) {
                            return determineAppsPassingContainsOperatorFilter(subjectsById.keySet(), ratingSchemeItemById, filter, cellDataForColumn);
                        } else if (filter.filterOperator().equals(FilterOperator.CONTAINS_ANY_STRING)) {
                            return determineAppsPassingContainsStringFilter(filter, cellDataForColumn);
                        } else {
                            return subjectsById.keySet(); // return all apps if filter operator not supported to support intersection
                        }
                    })
                    .collect(Collectors.toSet());

            return appIdsPassingFilters
                    .stream()
                    .reduce(first(appIdsPassingFilters), SetUtilities::intersection)
                    .stream()
                    .map(subjectsById::get)
                    .collect(Collectors.toSet());
        }
    }


    private Set<Long> determineAppsPassingContainsStringFilter(GridFilter filter,
                                                               Collection<ReportGridCell> cellDataForColumn) {
        return cellDataForColumn
                .stream()
                .filter(c -> notEmpty(c.textValue()) && containsAny(filter.filterValues(), c.textValue()))
                .map(ReportGridCell::subjectId)
                .collect(Collectors.toSet());
    }


    private boolean containsAny(Set<String> searchStrings, String lookupString) {
        for (String text : searchStrings) {
            if (lookupString.contains(text)) {
                return true;
            }
        }
        return false;
    }


    private Set<Long> determineAppsPassingContainsOperatorFilter(Set<Long> subjectIds,
                                                                 Map<Long, RatingSchemeItem> ratingSchemeItemByIdMap,
                                                                 GridFilter filter,
                                                                 Collection<ReportGridCell> cellDataForColumn) {
        Set<Long> appsPassingFilter = cellDataForColumn
                .stream()
                .filter(c -> {
                    // rating cells may want to look up on rating id / code / external id
                    if (!isEmpty(c.ratingIdValues())) {
                        Set<RatingSchemeItem> ratings = SetUtilities.map(
                                c.ratingIdValues(),
                                ratingSchemeItemByIdMap::get);
                        Set<String> ratingIdentifiers = union(
                                map(c.options(), CellOption::code),
                                map(ratings, rating -> String.valueOf(rating.rating())),
                                map(ratings, NameProvider::name),
                                map(ratings, rating -> rating.externalId().orElse(null)));
                        return notEmpty(intersection(filter.filterValues(), ratingIdentifiers));
                    } else {
                        Set<String> optionCodes = SetUtilities.map(c.options(), CellOption::code);
                        return notEmpty(intersection(filter.filterValues(), optionCodes));
                    }
                })
                .map(ReportGridCell::subjectId)
                .collect(Collectors.toSet());

        if (filter.filterValues().contains(NOT_PROVIDED_OPTION_CODE)) {
            Set<Long> subjectIdsWithValues = SetUtilities.map(cellDataForColumn, ReportGridCell::subjectId);
            Set<Long> subjectIdsWithoutValue = minus(subjectIds, subjectIdsWithValues);

            return union(appsPassingFilter, subjectIdsWithoutValue);
        } else {
            return appsPassingFilter;
        }
    }


    private Set<ReportGridFilterInfo> findGridInfoWithFilters() {

        Set<EntityNamedNote> filterPresetNotes = entityNamedNoteService.findByNoteTypeExtId(REPORT_GRID_APP_GROUP_CREATION_NOTE_TYPE_EXT_ID);

        Set<ReportGridDefinition> grids = reportGridDao.findAllDefinitions();
        Map<String, ReportGridDefinition> gridsByExternalId = indexBy(grids, d -> d.externalId().get());

        List<Tuple2<Long, String>> appGroupIdToNoteText = map(filterPresetNotes, d -> tuple(d.entityReference().id(), d.noteText()));

        return appGroupIdToNoteText
                .stream()
                .map(t -> parseGridFilterInfo(t.v1, t.v2))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

    }


    private ReportGridFilterInfo parseGridFilterInfo(Long appGroupId,
                                                     String noteText) {

        Tuple2<List<String>, List<List<String>>> gridInfoAndFilters;

        try {
            gridInfoAndFilters = parseGridFilterNoteText(noteText);
            //Should only be one row for grid information
            List<String> gridInfoRow = gridInfoAndFilters.v1;
            List<List<String>> filterRows = gridInfoAndFilters.v2;
            ReportGridDefinition grid = loadGrid(gridInfoRow.get(1));

            Set<GridFilter> filterValues = parseGridFilters(filterRows, grid);

            return ImmutableReportGridFilterInfo.builder()
                    .appGroupId(appGroupId)
                    .idSelectionOptions(mkSelectionOptionsFromGridInfoRow(gridInfoRow))
                    .gridDefinition(grid)
                    .gridFilters(filterValues)
                    .build();

        } catch (IllegalStateException e) {
            LOG.debug("Could not parse note text. " + e.getMessage());
            return null;
        }
    }


    private IdSelectionOptions mkSelectionOptionsFromGridInfoRow(List<String> gridInfo) {
        String vantagePointKind = gridInfo.get(2);
        String vantagePointId = gridInfo.get(3);
        return mkOpts(mkRef(
                EntityKind.valueOf(vantagePointKind),
                Long.parseLong(vantagePointId)));
    }


    private ReportGridDefinition loadGrid(String gridExtId) {
        ReportGridDefinition grid = reportGridService.getGridDefinitionByExtId(gridExtId);
        if (grid == null) {
            throw new IllegalStateException(format(
                    "Cannot identify grid '%s' from note",
                    gridExtId));
        }
        return grid;
    }
}
