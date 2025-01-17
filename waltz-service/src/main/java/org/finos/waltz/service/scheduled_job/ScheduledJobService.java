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

package org.finos.waltz.service.scheduled_job;


import org.finos.waltz.common.ExcludeFromIntegrationTesting;
import org.finos.waltz.data.assessment_rating.AssessmentRatingRippler;
import org.finos.waltz.data.scheduled_job.ScheduledJobDao;
import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.scheduled_job.JobKey;
import org.finos.waltz.model.scheduled_job.JobLifecycleStatus;
import org.finos.waltz.service.attestation.AttestationRunService;
import org.finos.waltz.service.complexity.ComplexityService;
import org.finos.waltz.service.cost.CostService;
import org.finos.waltz.service.entity_hierarchy.EntityHierarchyService;
import org.finos.waltz.service.flow_classification_rule.FlowClassificationRuleService;
import org.finos.waltz.service.logical_flow.LogicalFlowService;
import org.finos.waltz.service.physical_specification_data_type.PhysicalSpecDataTypeService;
import org.finos.waltz.service.report_grid.ReportGridFilterViewService;
import org.finos.waltz.service.survey.SurveyInstanceActionQueueService;
import org.finos.waltz.service.survey.SurveyInstanceService;
import org.finos.waltz.service.usage_info.DataTypeUsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

import static org.finos.waltz.common.Checks.checkNotNull;
import static org.finos.waltz.common.SetUtilities.asSet;

@ExcludeFromIntegrationTesting
@Service
public class ScheduledJobService {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledJobService.class);

    private final DataTypeUsageService dataTypeUsageService;
    private final EntityHierarchyService entityHierarchyService;
    private final FlowClassificationRuleService flowClassificationRuleService;
    private final LogicalFlowService logicalFlowService;
    private final PhysicalSpecDataTypeService physicalSpecDataTypeService;
    private final ScheduledJobDao scheduledJobDao;
    private final AttestationRunService attestationRunService;
    private final SurveyInstanceService surveyInstanceService;

    private final ReportGridFilterViewService reportGridFilterViewService;
    private final CostService costService;
    private final SurveyInstanceActionQueueService surveyInstanceActionQueueService;
    private final ComplexityService complexityService;
    private final AssessmentRatingRippler assessmentRatingRippler;


    @Autowired
    public ScheduledJobService(AttestationRunService attestationRunService,
                               ComplexityService complexityService,
                               CostService costService,
                               DataTypeUsageService dataTypeUsageService,
                               EntityHierarchyService entityHierarchyService,
                               FlowClassificationRuleService flowClassificationRuleService,
                               LogicalFlowService logicalFlowService,
                               PhysicalSpecDataTypeService physicalSpecDataTypeService,
                               ReportGridFilterViewService reportGridFilterViewService,
                               ScheduledJobDao scheduledJobDao,
                               SurveyInstanceActionQueueService surveyInstanceActionQueueService,
                               SurveyInstanceService surveyInstanceService,
                               AssessmentRatingRippler assessmentRatingRippler) {


        checkNotNull(attestationRunService, "attestationRunService cannot be null");
        checkNotNull(complexityService, "complexityService cannot be null");
        checkNotNull(costService, "costService cannot be null");
        checkNotNull(dataTypeUsageService, "dataTypeUsageService cannot be null");
        checkNotNull(flowClassificationRuleService, "flowClassificationRuleService cannot be null");
        checkNotNull(logicalFlowService, "logicalFlowService cannot be null");
        checkNotNull(physicalSpecDataTypeService, "physicalSpecDataTypeService cannot be null");
        checkNotNull(reportGridFilterViewService, "reportGridFilterViewService cannot be null");
        checkNotNull(scheduledJobDao, "scheduledJobDao cannot be null");
        checkNotNull(surveyInstanceActionQueueService, "surveyInstanceActionQueueService cannot be null");
        checkNotNull(surveyInstanceService, "surveyInstanceService cannot be null");
        checkNotNull(assessmentRatingRippler, "assessmentRatingRippler cannot be null");

        this.attestationRunService = attestationRunService;
        this.complexityService = complexityService;
        this.costService = costService;
        this.dataTypeUsageService = dataTypeUsageService;
        this.entityHierarchyService = entityHierarchyService;
        this.flowClassificationRuleService = flowClassificationRuleService;
        this.logicalFlowService = logicalFlowService;
        this.physicalSpecDataTypeService = physicalSpecDataTypeService;
        this.reportGridFilterViewService = reportGridFilterViewService;
        this.scheduledJobDao = scheduledJobDao;
        this.surveyInstanceActionQueueService = surveyInstanceActionQueueService;
        this.surveyInstanceService = surveyInstanceService;
        this.assessmentRatingRippler = assessmentRatingRippler;
    }


    @Scheduled(fixedRate = 300_000)
    public void run() {
        Thread.currentThread().setName("WaltzScheduledJobService");
        runIfNeeded(JobKey.HIERARCHY_REBUILD_CHANGE_INITIATIVE,
                (jk) -> entityHierarchyService.buildFor(EntityKind.CHANGE_INITIATIVE));

        runIfNeeded(JobKey.HIERARCHY_REBUILD_DATA_TYPE,
                (jk) -> entityHierarchyService.buildFor(EntityKind.DATA_TYPE));

        runIfNeeded(JobKey.HIERARCHY_REBUILD_ENTITY_STATISTICS,
                (jk) -> entityHierarchyService.buildFor(EntityKind.ENTITY_STATISTIC));

        runIfNeeded(JobKey.HIERARCHY_REBUILD_MEASURABLE,
                (jk) -> entityHierarchyService.buildFor(EntityKind.MEASURABLE));

        runIfNeeded(JobKey.HIERARCHY_REBUILD_ORG_UNIT,
                (jk) -> entityHierarchyService.buildFor(EntityKind.ORG_UNIT));

        runIfNeeded(JobKey.HIERARCHY_REBUILD_PERSON,
                (jk) -> entityHierarchyService.buildFor(EntityKind.PERSON));

        runIfNeeded(JobKey.DATA_TYPE_RIPPLE_PHYSICAL_TO_LOGICAL,
                (jk) -> physicalSpecDataTypeService.rippleDataTypesToLogicalFlows(),
                asSet(JobKey.DATA_TYPE_USAGE_RECALC_APPLICATION));

        runIfNeeded(JobKey.DATA_TYPE_USAGE_RECALC_APPLICATION,
                (jk) -> dataTypeUsageService.recalculateForAllApplications(),
                asSet(JobKey.DATA_TYPE_RIPPLE_PHYSICAL_TO_LOGICAL));

        runIfNeeded(JobKey.AUTH_SOURCE_RECALC_FLOW_RATINGS,
                (jk) -> flowClassificationRuleService.fastRecalculateAllFlowRatings());

        runIfNeeded(JobKey.LOGICAL_FLOW_CLEANUP_ORPHANS,
                (jk) -> logicalFlowService.cleanupOrphans());

        runIfNeeded(JobKey.ATTESTATION_ISSUE_INSTANCES,
                (jk) -> attestationRunService.issueInstancesForPendingRuns());

        runIfNeeded(JobKey.SURVEY_INSTANCE_REASSIGN_RECIPIENTS,
                (jk) -> surveyInstanceService.reassignRecipients());

        runIfNeeded(JobKey.SURVEY_INSTANCE_REASSIGN_OWNERS,
                (jk) -> surveyInstanceService.reassignOwners());

        runIfNeeded(JobKey.REPORT_GRID_RECALCULATE_APP_GROUPS_FROM_FILTERS,
                (jk) -> reportGridFilterViewService.generateAppGroupsFromFilter());

        runIfNeeded(JobKey.ALLOCATED_COSTS_POPULATOR,
                (jk) -> costService.populateAllocatedCosts());

        runIfNeeded(JobKey.COMPLEXITY_REBUILD_MEASURABLE,
                (jk) -> complexityService.populateMeasurableComplexities());

        runIfNeeded(JobKey.RIPPLE_ASSESSMENTS,
                (jk) -> assessmentRatingRippler.rippleAssessments());

        surveyInstanceActionQueueService.performActions();

    }


    private void runIfNeeded(JobKey jobKey, Consumer<JobKey> jobExecutor) {
        runIfNeeded(jobKey, jobExecutor, Collections.emptySet());
    }

    private void runIfNeeded(JobKey jobKey, Consumer<JobKey> jobExecutor, Set<JobKey> deadlockJobKeys) {
        try {
            if (scheduledJobDao.isJobRunnable(jobKey)
                    && !scheduledJobDao.anyJobsRunning(deadlockJobKeys)
                    && scheduledJobDao.markJobAsRunning(jobKey)) {
                jobExecutor
                        .andThen((jk) -> scheduledJobDao.updateJobStatus(jk, JobLifecycleStatus.COMPLETED))
                        .accept(jobKey);
            }
        } catch (Exception e) {
            LOG.error("Failed to run job: " + jobKey, e);
            scheduledJobDao.updateJobStatus(jobKey, JobLifecycleStatus.ERRORED);
        }
    }


}
