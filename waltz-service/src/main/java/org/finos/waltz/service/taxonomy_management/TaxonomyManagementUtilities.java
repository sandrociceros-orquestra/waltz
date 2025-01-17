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

package org.finos.waltz.service.taxonomy_management;

import org.finos.waltz.common.SetUtilities;
import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.EntityReference;
import org.finos.waltz.model.HierarchyQueryScope;
import org.finos.waltz.model.IdSelectionOptions;
import org.finos.waltz.model.Severity;
import org.finos.waltz.model.exceptions.NotAuthorizedException;
import org.finos.waltz.model.measurable.Measurable;
import org.finos.waltz.model.measurable_category.MeasurableCategory;
import org.finos.waltz.model.measurable_rating.MeasurableRating;
import org.finos.waltz.model.taxonomy_management.ImmutableTaxonomyChangeImpact;
import org.finos.waltz.model.taxonomy_management.ImmutableTaxonomyChangePreview;
import org.finos.waltz.model.taxonomy_management.TaxonomyChangeCommand;
import org.finos.waltz.model.user.SystemRole;
import org.finos.waltz.service.measurable.MeasurableService;
import org.finos.waltz.service.measurable_category.MeasurableCategoryService;
import org.finos.waltz.service.measurable_rating.MeasurableRatingService;
import org.finos.waltz.service.user.UserRoleService;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.finos.waltz.common.Checks.*;
import static org.finos.waltz.common.CollectionUtilities.any;
import static org.finos.waltz.common.SetUtilities.fromCollection;
import static org.finos.waltz.common.SetUtilities.minus;
import static org.finos.waltz.model.IdSelectionOptions.mkOpts;

public class TaxonomyManagementUtilities {


    public static Measurable validatePrimaryMeasurable(MeasurableService measurableService,
                                                       TaxonomyChangeCommand cmd) {
        long measurableId = cmd.primaryReference().id();
        long categoryId = cmd.changeDomain().id();
        return validateMeasurableInCategory(measurableService, measurableId, categoryId);
    }

    public static void validateMeasurablesInCategory(MeasurableService measurableService,
                                                        List<Long> ids,
                                                        long categoryId) {
        List<Measurable> allMeasurablesInCategory = measurableService.findByCategoryId(categoryId);

        checkEmpty(
            minus(
                fromCollection(ids),
                SetUtilities.map(
                    allMeasurablesInCategory,
                    m -> m.id().get())),
            format(
                "Not all measurables: (%s) in category: %d",
                ids.toString(),
                categoryId));
    }


    public static Measurable validateMeasurableInCategory(MeasurableService measurableService,
                                                          long measurableId,
                                                          long categoryId) {
        Measurable measurable = measurableService.getById(measurableId);

        checkNotNull(
                measurable,
                "Cannot find measurable [%d]",
                measurableId);

        checkTrue(
                categoryId == measurable.categoryId(),
                "Measurable [%s / %d] is not in category [%d], instead it is in category [%d]",
                measurable.name(),
                measurable.id(),
                categoryId,
                measurable.categoryId());

        return measurable;
    }

    public static void validateTargetNotChild(MeasurableService measurableService,
                                              Measurable measurable,
                                              Measurable targetMeasurable) {

        List<Measurable> children = measurableService.findByMeasurableIdSelector(mkOpts(
                measurable.entityReference(),
                HierarchyQueryScope.CHILDREN));

        checkFalse(
                any(children, d -> d.equals(targetMeasurable)),
                format("Target measurable [%s / %d] is a child of measurable [%s / %d]",
                        targetMeasurable.name(),
                        targetMeasurable.id().get(),
                        measurable.name(),
                        measurable.id().get()));
    }


    public static void validateConcreteMergeAllowed(Measurable measurable,
                                                    Measurable targetMeasurable) {

        checkFalse(
                measurable.concrete() && !targetMeasurable.concrete(),
                format("Measurable [%s / %d] is concrete but target measurable [%s / %d] is abstract",
                        measurable.name(),
                        measurable.id().get(),
                        targetMeasurable.name(),
                        targetMeasurable.id().get()));
    }


    public static Set<EntityReference> findCurrentRatingMappings(MeasurableRatingService measurableRatingService,
                                                                 TaxonomyChangeCommand cmd) {
        IdSelectionOptions selectionOptions = mkOpts(cmd.primaryReference(), HierarchyQueryScope.EXACT);
        return measurableRatingService
                .findByMeasurableIdSelector(selectionOptions)
                .stream()
                .map(MeasurableRating::entityReference)
                .collect(Collectors.toSet());
    }


    /**
     * Optionally add an impact to the given preview and return it.
     * Whether to add the impact is determined by the presence of references.
     *
     * @param preview     The preview builder to update
     * @param impactCount Count of the records affected by this change
     * @param severity    Severity of the impact
     * @param msg         Description of the impact
     * @return The preview builder for convenience
     */
    public static ImmutableTaxonomyChangePreview.Builder addToPreview(ImmutableTaxonomyChangePreview.Builder preview,
                                                                      int impactCount,
                                                                      Severity severity,
                                                                      String msg) {
        return impactCount == 0
                ? preview
                : preview
                .addImpacts(ImmutableTaxonomyChangeImpact.builder()
                        .impactCount(impactCount)
                        .description(msg)
                        .severity(severity)
                        .build());
    }


    public static String getNameParam(TaxonomyChangeCommand cmd) {
        return cmd.param("name");
    }


    public static String getDescriptionParam(TaxonomyChangeCommand cmd) {
        return cmd.param("description");
    }


    public static String getExternalIdParam(TaxonomyChangeCommand cmd) {
        return cmd.param("externalId");
    }


    public static boolean getConcreteParam(TaxonomyChangeCommand cmd, boolean dflt) {
        return cmd.paramAsBoolean("concrete", dflt);
    }

    public static void verifyUserHasPermissions(MeasurableCategoryService measurableCategoryService,
                                         UserRoleService userRoleService,
                                         String userId,
                                         EntityReference changeDomain) {
        verifyUserHasPermissions(userRoleService, userId);

        if (changeDomain.kind() == EntityKind.MEASURABLE_CATEGORY) {
            MeasurableCategory category = measurableCategoryService.getById(changeDomain.id());
            if (!category.editable()) {
                throw new NotAuthorizedException("Unauthorised: Category is not editable");
            }
        }
    }

    public static void verifyUserHasPermissions(UserRoleService userRoleService, String userId) {
        if (!userRoleService.hasRole(userId, SystemRole.TAXONOMY_EDITOR.name())) {
            throw new NotAuthorizedException();
        }
    }

}
