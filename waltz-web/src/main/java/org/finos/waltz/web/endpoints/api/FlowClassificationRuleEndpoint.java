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

package org.finos.waltz.web.endpoints.api;


import org.finos.waltz.model.flow_classification_rule.FlowClassificationRuleView;
import org.finos.waltz.service.flow_classification_rule.FlowClassificationRuleService;
import org.finos.waltz.service.flow_classification_rule.FlowClassificationRuleViewService;
import org.finos.waltz.service.user.UserRoleService;
import org.finos.waltz.web.DatumRoute;
import org.finos.waltz.web.ListRoute;
import org.finos.waltz.web.endpoints.Endpoint;
import org.finos.waltz.model.EntityReference;
import org.finos.waltz.model.Entry;
import org.finos.waltz.model.IdSelectionOptions;
import org.finos.waltz.model.flow_classification_rule.DiscouragedSource;
import org.finos.waltz.model.flow_classification_rule.FlowClassificationRule;
import org.finos.waltz.model.flow_classification_rule.FlowClassificationRuleCreateCommand;
import org.finos.waltz.model.flow_classification_rule.FlowClassificationRuleUpdateCommand;
import org.finos.waltz.model.user.SystemRole;
import org.finos.waltz.web.WebUtilities;
import org.finos.waltz.web.endpoints.EndpointUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.finos.waltz.common.Checks.checkNotNull;
import static org.finos.waltz.web.WebUtilities.mkPath;
import static org.finos.waltz.web.WebUtilities.readIdSelectionOptionsFromBody;


@Service
public class FlowClassificationRuleEndpoint implements Endpoint {

    private static final Logger LOG = LoggerFactory.getLogger(FlowClassificationRuleEndpoint.class);
    private static final String BASE_URL = mkPath("api", "flow-classification-rule");

    private final FlowClassificationRuleService flowClassificationRuleService;
    private final UserRoleService userRoleService;
    private final FlowClassificationRuleViewService flowClassificationRuleViewService;


    @Autowired
    public FlowClassificationRuleEndpoint(FlowClassificationRuleService flowClassificationRuleService,
                                          UserRoleService userRoleService,
                                          FlowClassificationRuleViewService flowClassificationRuleViewService) {
        checkNotNull(flowClassificationRuleService, "flowClassificationRuleService must not be null");
        checkNotNull(flowClassificationRuleViewService, "flowClassificationRuleViewService must not be null");
        checkNotNull(userRoleService, "userRoleService cannot be null");

        this.flowClassificationRuleService = flowClassificationRuleService;
        this.userRoleService = userRoleService;
        this.flowClassificationRuleViewService = flowClassificationRuleViewService;
    }


    @Override
    public void register() {

        // -- PATHS

        String recalculateFlowRatingsPath = mkPath(BASE_URL, "recalculate-flow-ratings");
        String findDiscouragedSourcesPath = mkPath(BASE_URL, "discouraged");
        String findFlowClassificationRulesBySelectorPath = mkPath(BASE_URL, "selector");
        String calculateConsumersForDataTypeIdSelectorPath = mkPath(BASE_URL, "data-type", "consumers");
        String findByEntityReferencePath = mkPath(BASE_URL, "entity-ref", ":kind", ":id");
        String findByApplicationIdPath = mkPath(BASE_URL, "app", ":id");
        String findCompanionEntityRulesPath = mkPath(BASE_URL, "companion-rules", "entity", "id", ":id");
        String findCompanionDataTypeRulesPath = mkPath(BASE_URL, "companion-rules", "data-type", "id", ":id");
        String getByIdPath = mkPath(BASE_URL, "id", ":id");
        String deletePath = mkPath(BASE_URL, "id", ":id");
        String cleanupOrphansPath = mkPath(BASE_URL, "cleanup-orphans");
        String getViewForSelectorPath = mkPath(BASE_URL, "view");


        // -- ROUTES

        ListRoute<FlowClassificationRule> findByEntityReferenceRoute = (request, response)
                -> flowClassificationRuleService.findByEntityReference(WebUtilities.getEntityReference(request));

        ListRoute<FlowClassificationRule> findByApplicationIdRoute = (request, response)
                -> flowClassificationRuleService.findByApplicationId(WebUtilities.getId(request));

        ListRoute<DiscouragedSource> findDiscouragedSourcesRoute = (request, response)
                -> flowClassificationRuleService.findDiscouragedSources(readIdSelectionOptionsFromBody(request));

        ListRoute<FlowClassificationRule> findFlowClassificationRulesBySelectorRoute = (request, response)
                -> flowClassificationRuleService.findClassificationRules(readIdSelectionOptionsFromBody(request));

        ListRoute<FlowClassificationRule> findAllRoute = (request, response)
                -> flowClassificationRuleService.findAll();

        ListRoute<FlowClassificationRule> findCompanionEntityRulesRoute = (request, response)
                -> flowClassificationRuleService.findCompanionEntityRules(WebUtilities.getId(request));

        ListRoute<FlowClassificationRule> findCompanionDataTypeRulesRoute = (request, response)
                -> flowClassificationRuleService.findCompanionDataTypeRules(WebUtilities.getId(request));

        DatumRoute<FlowClassificationRule> getByIdRoute = (request, response)
                -> flowClassificationRuleService.getById(WebUtilities.getId(request));
        
        DatumRoute<FlowClassificationRuleView> getViewForSelectorRoute = (request, response)
                -> flowClassificationRuleViewService.getViewForSelector(readIdSelectionOptionsFromBody(request));

        EndpointUtilities.getForDatum(recalculateFlowRatingsPath, this::recalculateFlowRatingsRoute);
        EndpointUtilities.getForDatum(cleanupOrphansPath, this::cleanupOrphansRoute);
        EndpointUtilities.getForDatum(getByIdPath, getByIdRoute);
        EndpointUtilities.postForList(calculateConsumersForDataTypeIdSelectorPath, this::calculateConsumersForDataTypeIdSelectorRoute);
        EndpointUtilities.postForList(findDiscouragedSourcesPath, findDiscouragedSourcesRoute);
        EndpointUtilities.getForList(findByEntityReferencePath, findByEntityReferenceRoute);
        EndpointUtilities.getForList(findByApplicationIdPath, findByApplicationIdRoute);
        EndpointUtilities.postForList(findFlowClassificationRulesBySelectorPath, findFlowClassificationRulesBySelectorRoute);
        EndpointUtilities.getForList(findCompanionEntityRulesPath, findCompanionEntityRulesRoute);
        EndpointUtilities.getForList(findCompanionDataTypeRulesPath, findCompanionDataTypeRulesRoute);
        EndpointUtilities.postForDatum(getViewForSelectorPath, getViewForSelectorRoute);
        EndpointUtilities.getForList(BASE_URL, findAllRoute);
        EndpointUtilities.putForDatum(BASE_URL, this::updateRoute);
        EndpointUtilities.postForDatum(BASE_URL, this::insertRoute);
        EndpointUtilities.deleteForDatum(deletePath, this::deleteRoute);
    }


    private Integer cleanupOrphansRoute(Request request, Response response) throws IOException {
        WebUtilities.requireRole(userRoleService, request, SystemRole.ADMIN);

        String username = WebUtilities.getUsername(request);

        LOG.info("User: {}, requested auth source cleanup", username);
        return flowClassificationRuleService.cleanupOrphans(username);
    }


    private String insertRoute(Request request, Response response) throws IOException {
        WebUtilities.requireRole(userRoleService, request, SystemRole.AUTHORITATIVE_SOURCE_EDITOR);
        FlowClassificationRuleCreateCommand command = WebUtilities.readBody(request, FlowClassificationRuleCreateCommand.class);
        flowClassificationRuleService.insert(command, WebUtilities.getUsername(request));
        return "done";
    }


    private String deleteRoute(Request request, Response response) {
        WebUtilities.requireRole(userRoleService, request, SystemRole.AUTHORITATIVE_SOURCE_EDITOR);
        long id = WebUtilities.getId(request);
        flowClassificationRuleService.remove(id, WebUtilities.getUsername(request));
        return "done";
    }


    private String updateRoute(Request request, Response response) throws IOException {
        WebUtilities.requireRole(userRoleService, request, SystemRole.AUTHORITATIVE_SOURCE_EDITOR);
        FlowClassificationRuleUpdateCommand command = WebUtilities.readBody(request, FlowClassificationRuleUpdateCommand.class);
        flowClassificationRuleService.update(command, WebUtilities.getUsername(request));
        return "done";
    }


    private int recalculateFlowRatingsRoute(Request request, Response response) {
        WebUtilities.requireRole(userRoleService, request, SystemRole.ADMIN);

        String username = WebUtilities.getUsername(request);
        LOG.info("Recalculating all flow ratings (requested by: {})", username);

        return flowClassificationRuleService.fastRecalculateAllFlowRatings();
    }


    private List<Entry<EntityReference, Collection<EntityReference>>> calculateConsumersForDataTypeIdSelectorRoute(
            Request request,
            Response response) throws IOException {

        IdSelectionOptions options = readIdSelectionOptionsFromBody(request);

        Map<EntityReference, Collection<EntityReference>> result = flowClassificationRuleService
                .calculateConsumersForDataTypeIdSelector(options);

        return WebUtilities.simplifyMapToList(result);
    }

}
