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

package org.finos.waltz.service.app_group;

import org.finos.waltz.common.Checks;
import org.finos.waltz.common.SetUtilities;
import org.finos.waltz.common.exception.InsufficientPrivelegeException;
import org.finos.waltz.data.app_group.AppGroupDao;
import org.finos.waltz.data.app_group.AppGroupEntryDao;
import org.finos.waltz.data.app_group.AppGroupMemberDao;
import org.finos.waltz.data.app_group.AppGroupOrganisationalUnitDao;
import org.finos.waltz.data.application.ApplicationDao;
import org.finos.waltz.data.entity_relationship.EntityRelationshipDao;
import org.finos.waltz.data.orgunit.OrganisationalUnitDao;
import org.finos.waltz.model.DiffResult;
import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.EntityReference;
import org.finos.waltz.model.ImmutableEntityReference;
import org.finos.waltz.model.Operation;
import org.finos.waltz.model.Severity;
import org.finos.waltz.model.app_group.AppGroup;
import org.finos.waltz.model.app_group.AppGroupDetail;
import org.finos.waltz.model.app_group.AppGroupEntry;
import org.finos.waltz.model.app_group.AppGroupKind;
import org.finos.waltz.model.app_group.AppGroupMember;
import org.finos.waltz.model.app_group.AppGroupMemberRole;
import org.finos.waltz.model.app_group.ImmutableAppGroup;
import org.finos.waltz.model.app_group.ImmutableAppGroupDetail;
import org.finos.waltz.model.application.Application;
import org.finos.waltz.model.changelog.ChangeLog;
import org.finos.waltz.model.changelog.ImmutableChangeLog;
import org.finos.waltz.model.entity_relationship.EntityRelationship;
import org.finos.waltz.model.entity_relationship.ImmutableEntityRelationship;
import org.finos.waltz.model.entity_relationship.RelationshipKind;
import org.finos.waltz.model.entity_search.EntitySearchOptions;
import org.finos.waltz.model.orgunit.OrganisationalUnit;
import org.finos.waltz.service.change_initiative.ChangeInitiativeService;
import org.finos.waltz.service.changelog.ChangeLogService;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.finos.waltz.common.Checks.checkNotNull;
import static org.finos.waltz.common.ListUtilities.append;
import static org.finos.waltz.common.MapUtilities.indexBy;
import static org.finos.waltz.data.JooqUtilities.summarizeResults;
import static org.finos.waltz.model.DiffResult.mkDiff;
import static org.finos.waltz.model.EntityReference.mkRef;
import static org.jooq.lambda.tuple.Tuple.tuple;

@Service
public class AppGroupService {

    private static final Logger LOG = LoggerFactory.getLogger(AppGroupService.class);

    private final AppGroupDao appGroupDao;
    private final AppGroupMemberDao appGroupMemberDao;
    private final AppGroupEntryDao appGroupEntryDao;
    private final ApplicationDao applicationDao;
    private final AppGroupOrganisationalUnitDao appGroupOrganisationalUnitDao;
    private final OrganisationalUnitDao organisationalUnitDao;
    private final EntityRelationshipDao entityRelationshipDao;
    private final ChangeInitiativeService changeInitiativeService;
    private final ChangeLogService changeLogService;


    @Autowired
    public AppGroupService(AppGroupDao appGroupDao,
                           AppGroupMemberDao appGroupMemberDao,
                           AppGroupEntryDao appGroupEntryDao,
                           ApplicationDao applicationDao,
                           AppGroupOrganisationalUnitDao appGroupOrganisationalUnitDao,
                           OrganisationalUnitDao organisationalUnitDao,
                           EntityRelationshipDao entityRelationshipDao,
                           ChangeInitiativeService changeInitiativeService,
                           ChangeLogService changeLogService) {
        checkNotNull(appGroupDao, "appGroupDao cannot be null");
        checkNotNull(appGroupEntryDao, "appGroupEntryDao cannot be null");
        checkNotNull(appGroupEntryDao, "appGroupEntryDao cannot be null");
        checkNotNull(applicationDao, "applicationDao cannot be null");
        checkNotNull(appGroupOrganisationalUnitDao, "appGroupOrganisationalUnitDao cannot be null");
        checkNotNull(organisationalUnitDao, "organisationalUnitDao cannot be null");
        checkNotNull(entityRelationshipDao, "entityRelationshipDao cannot be null");
        checkNotNull(changeInitiativeService, "changeInitiativeService cannot be null");
        checkNotNull(changeLogService, "changeLogService cannot be null");

        this.appGroupDao = appGroupDao;
        this.appGroupMemberDao = appGroupMemberDao;
        this.appGroupEntryDao = appGroupEntryDao;
        this.applicationDao = applicationDao;
        this.appGroupOrganisationalUnitDao = appGroupOrganisationalUnitDao;
        this.organisationalUnitDao = organisationalUnitDao;
        this.entityRelationshipDao = entityRelationshipDao;
        this.changeInitiativeService = changeInitiativeService;
        this.changeLogService = changeLogService;
    }


    public AppGroupDetail getGroupDetailById(long groupId) {
        AppGroup group = appGroupDao.getGroup(groupId);
        if (group == null) return null;

        return ImmutableAppGroupDetail
                .builder()
                .appGroup(group)
                .applications(appGroupEntryDao.findEntriesForGroup(groupId))
                .members(appGroupMemberDao.getMembers(groupId))
                .organisationalUnits(appGroupOrganisationalUnitDao.getEntriesForGroup(groupId))
                .changeInitiatives(changeInitiativeService.findEntriesForAppGroup(groupId))
                .build();
    }


    public Set<AppGroupSubscription> findGroupSubscriptionsForUser(String userId) {
        Set<AppGroupMember> subscriptions = appGroupMemberDao.getSubscriptions(userId);
        Map<Long, AppGroupMemberRole> roleByGroup = indexBy(
                AppGroupMember::groupId,
                AppGroupMember::role,
                subscriptions);

        List<AppGroup> groups = appGroupDao.findGroupsForUser(userId);

        return groups
                .stream()
                .map(g -> ImmutableAppGroupSubscription.builder()
                        .appGroup(g)
                        .role(roleByGroup.get(g.id().get()))
                        .build())
                .collect(Collectors.toSet());
    }


    public List<AppGroup> findPublicGroups() {
        return appGroupDao.findPublicGroups();
    }


    public List<AppGroup> findPrivateGroupsByOwner(String ownerId) {
        return appGroupDao.findPrivateGroupsByOwner(ownerId);
    }


    public List<AppGroup> findRelatedByEntityReference(EntityReference ref, String username) {
        switch (ref.kind()) {
            case APPLICATION:
                return appGroupDao.findRelatedByApplicationId(ref.id(), username);
            default:
                return appGroupDao.findRelatedByEntityReference(ref, username);
        }
    }


    public List<AppGroup> search(EntitySearchOptions options) {
        return appGroupDao.search(options);
    }


    public void subscribe(String userId, long groupId) {
        AppGroup group = appGroupDao.getGroup(groupId);

        if (group.isFavouriteGroup()){
            throw new IllegalArgumentException("Cannot subscribe to someone else's favourites group");
        } else {
            audit(groupId,
                    userId,
                    "Subscribed to group " + group.name(),
                    EntityKind.PERSON,
                    null,
                    Operation.ADD);
            appGroupMemberDao.register(groupId, userId);
        }
    }


    public void unsubscribe(String userId, long groupId) {
        audit(groupId,
                userId,
                "Unsubscribed from group" + appGroupDao.getGroup(groupId).name(),
                EntityKind.PERSON,
                null,
                Operation.REMOVE);
        appGroupMemberDao.unregister(groupId, userId);
    }


    public Set<AppGroupSubscription> deleteGroup(String userId, long groupId) throws InsufficientPrivelegeException {
        verifyUserCanUpdateGroup(userId, groupId);
        appGroupDao.deleteGroup(groupId);
        entityRelationshipDao.removeAnyInvolving(mkRef(EntityKind.APP_GROUP, groupId));
        audit(groupId, userId, format("Removed group %d", groupId), null, null, Operation.REMOVE);
        return findGroupSubscriptionsForUser(userId);
    }


    public List<AppGroupEntry> addApplication(String userId, long groupId, long applicationId) throws InsufficientPrivelegeException {

        verifyUserCanUpdateGroup(userId, groupId);

        Application app = applicationDao.getById(applicationId);
        if (app != null) {
            appGroupEntryDao.addApplication(groupId, applicationId);
            audit(groupId, userId, format("Added application %s to group", app.name()), EntityKind.APPLICATION, applicationId, Operation.ADD);
        }

        return appGroupEntryDao.findEntriesForGroup(groupId);
    }


    public List<AppGroupEntry> addApplications(String userId,
                                                 long groupId,
                                                 Collection<Long> applicationIds,
                                                 Collection<String> unknownIdentifiers) throws InsufficientPrivelegeException {
        verifyUserCanUpdateGroup(userId, groupId);

        appGroupEntryDao.addApplications(groupId, applicationIds);

        EntityReference entityReference = mkRef(EntityKind.APP_GROUP, groupId);
        List<Application> apps = applicationDao.findByIds(applicationIds);

        List<ChangeLog> addedApplicationChangeLogs = apps
                .stream()
                .map(app -> ImmutableChangeLog.builder()
                            .message(format("Added application %s to group", app.name()))
                            .userId(userId)
                            .parentReference(entityReference)
                            .childKind(EntityKind.APPLICATION)
                            .childId(app.id())
                            .operation(Operation.ADD)
                            .build())
                .collect(Collectors.toList());

        List<ChangeLog> changeLogs = (unknownIdentifiers.size() == 0)
                ? addedApplicationChangeLogs
                : append(addedApplicationChangeLogs,
                        ImmutableChangeLog.builder()
                                .message(format("The following {%d} identifiers could not be found and were not added to this group: %s", unknownIdentifiers.size(), unknownIdentifiers))
                                .userId(userId)
                                .parentReference(entityReference)
                                .childKind(EntityKind.APPLICATION)
                                .operation(Operation.UNKNOWN)
                                .build());

        changeLogService.write(changeLogs);

        return appGroupEntryDao.findEntriesForGroup(groupId);
    }


    public List<AppGroupEntry> removeApplication(String userId, long groupId, long applicationId) throws InsufficientPrivelegeException {
        verifyUserCanUpdateGroup(userId, groupId);
        appGroupEntryDao.removeApplication(groupId, applicationId);
        Application app = applicationDao.getById(applicationId);
        audit(groupId, userId, format(
                    "Removed application %s from group",
                    app != null
                        ? app.name()
                        : applicationId),
                EntityKind.APPLICATION,
                applicationId,
                Operation.REMOVE);
        return appGroupEntryDao.findEntriesForGroup(groupId);
    }

    public List<AppGroupEntry> addOrganisationalUnit(String userId, long groupId, long orgUnitId) throws InsufficientPrivelegeException {

        verifyUserCanUpdateGroup(userId, groupId);
        OrganisationalUnit orgUnit = organisationalUnitDao.getById(orgUnitId);
        if (orgUnit != null) {
            appGroupOrganisationalUnitDao.addOrgUnit(groupId, orgUnitId);
            audit(groupId, userId, format("Added application %s to group", orgUnit.name()), EntityKind.ORG_UNIT, orgUnitId, Operation.ADD);
        }
        return appGroupOrganisationalUnitDao.getEntriesForGroup(groupId);
    }

    public List<AppGroupEntry> removeOrganisationalUnit(String userId, long groupId, long orgUnitId) throws InsufficientPrivelegeException {
        verifyUserCanUpdateGroup(userId, groupId);
        appGroupOrganisationalUnitDao.removeOrgUnit(groupId, orgUnitId);
        OrganisationalUnit ou = organisationalUnitDao.getById(orgUnitId);
        audit(groupId, userId, format("Removed application %s from group", ou != null ? ou.name() : orgUnitId), EntityKind.ORG_UNIT, orgUnitId, Operation.REMOVE);
        return appGroupOrganisationalUnitDao.getEntriesForGroup(groupId);
    }
    public List<AppGroupEntry> removeApplications(String userId, long groupId, List<Long> applicationIds) throws InsufficientPrivelegeException {
        verifyUserCanUpdateGroup(userId, groupId);

        appGroupEntryDao.removeApplications(groupId, applicationIds);

        List<Application> apps = applicationDao.findByIds(applicationIds);
        List<ChangeLog> changeLogs = apps
                .stream()
                .map(app -> ImmutableChangeLog.builder()
                        .message(format("Removed application %s from group", app.name()))
                        .userId(userId)
                        .parentReference(ImmutableEntityReference.builder().id(groupId).kind(EntityKind.APP_GROUP).build())
                        .childKind(EntityKind.APPLICATION)
                        .operation(Operation.REMOVE)
                        .build())
                .collect(Collectors.toList());
        changeLogService.write(changeLogs);

        return appGroupEntryDao.findEntriesForGroup(groupId);
    }


    public int addOwner(String userId, long groupId, String ownerId) throws InsufficientPrivelegeException {
        verifyUserCanUpdateGroup(userId, groupId);
        audit(groupId, userId, format("Added owner %s to group %d", ownerId, groupId), EntityKind.PERSON, null, Operation.ADD);
        return appGroupMemberDao.register(groupId, ownerId, AppGroupMemberRole.OWNER);
    }


    public boolean removeOwner(String userId, long groupId, String ownerToRemoveId) throws InsufficientPrivelegeException {
        verifyUserCanUpdateGroup(userId, groupId);
        boolean result = appGroupMemberDao.unregister(groupId, ownerToRemoveId);
        subscribe(ownerToRemoveId, groupId);
        audit(groupId, userId, format("Removed owner %s from group %d", ownerToRemoveId, groupId), EntityKind.PERSON, null, Operation.REMOVE);
        return result;
    }


    public Set<AppGroupMember> getMembers(long groupId) {
        return appGroupMemberDao.getMembers(groupId);
    }


    public AppGroupDetail updateOverview(String userId, AppGroup appGroup) throws InsufficientPrivelegeException {
        verifyUserCanUpdateGroup(userId, appGroup.id().get());
        appGroupDao.update(appGroup);
        audit(appGroup.id().get(), userId, "Updated group overview", null, null, Operation.UPDATE);
        return getGroupDetailById(appGroup.id().get());
    }


    public Long createNewGroup(String userId) {
        long groupId = appGroupDao.insert(ImmutableAppGroup.builder()
                .description("New group created by: " + userId)
                .name("New group created by: " + userId)
                .appGroupKind(AppGroupKind.PRIVATE)
                .build());

        appGroupMemberDao.register(groupId, userId, AppGroupMemberRole.OWNER);

        audit(groupId, userId, "Created group", null, null, Operation.ADD);


        return groupId;
    }


    public Collection<AppGroup> findByIds(String user, List<Long> ids) {
        Checks.checkNotEmpty(user, "user cannot be empty");
        checkNotNull(ids, "ids cannot be null");
        return appGroupDao.findByIds(user, ids);
    }


    public List<AppGroupEntry> addChangeInitiative(
            String username,
            long groupId,
            long changeInitiativeId) throws InsufficientPrivelegeException {
        verifyUserCanUpdateGroup(username, groupId);

        EntityRelationship entityRelationship = buildChangeInitiativeRelationship(username, groupId, changeInitiativeId);
        entityRelationshipDao.save(entityRelationship);

        audit(groupId,
                username,
                format("Associated change initiative: %d", changeInitiativeId),
                EntityKind.CHANGE_INITIATIVE,
                changeInitiativeId,
                Operation.ADD);

        return changeInitiativeService.findEntriesForAppGroup(groupId);
    }


    public List<AppGroupEntry> removeChangeInitiative(
            String username,
            long groupId,
            long changeInitiativeId) throws InsufficientPrivelegeException {
        verifyUserCanUpdateGroup(username, groupId);

        EntityRelationship entityRelationship = buildChangeInitiativeRelationship(username, groupId, changeInitiativeId);
        entityRelationshipDao.remove(entityRelationship.toKey());

        audit(groupId,
                username,
                format("Removed associated change initiative: %d", changeInitiativeId),
                EntityKind.CHANGE_INITIATIVE,
                changeInitiativeId,
                Operation.REMOVE);

        return changeInitiativeService.findEntriesForAppGroup(groupId);
    }

    public List<AppGroupEntry> addChangeInitiatives(String userId, long groupId,
                                                    List<Long> changeInitiativeIds) throws InsufficientPrivelegeException {
        verifyUserCanUpdateGroup(userId, groupId);

        entityRelationshipDao.saveAll(userId, groupId, changeInitiativeIds);

        List<ChangeLog> changeInitiativeChangeLogs = changeInitiativeIds
                .stream()
                .map(ci -> ImmutableChangeLog.builder()
                        .message(format("Associated change initiative: %d", ci))
                        .userId(userId)
                        .parentReference(ImmutableEntityReference.builder().id(groupId).kind(EntityKind.APP_GROUP).build())
                .childKind(Optional.of(EntityKind.CHANGE_INITIATIVE))
                .operation(Operation.ADD)
                .build())
        .collect(Collectors.toList());
        changeLogService.write(changeInitiativeChangeLogs);

        return changeInitiativeService.findEntriesForAppGroup(groupId);
    }

    public List<AppGroupEntry> removeChangeInitiatives(String userId,
                                                       long groupId,
                                                       List<Long> changeInitiativeIds) throws InsufficientPrivelegeException {
        verifyUserCanUpdateGroup(userId, groupId);

        entityRelationshipDao.removeAll(groupId, changeInitiativeIds);

        List<ChangeLog> changeInitiativeChangeLogs = changeInitiativeIds
                .stream()
                .map(ci -> ImmutableChangeLog.builder()
                        .message(format("Removed associated change initiative: %d", ci))
                        .userId(userId)
                        .parentReference(ImmutableEntityReference.builder().id(groupId).kind(EntityKind.APP_GROUP).build())
                        .childKind(Optional.of(EntityKind.CHANGE_INITIATIVE))
                        .operation(Operation.REMOVE)
                        .build())
                .collect(Collectors.toList());

        changeLogService.write(changeInitiativeChangeLogs);

        return changeInitiativeService.findEntriesForAppGroup(groupId);
    }

    /*
    Removes all entries from groups and repopulates with the list of appGroupEntries
     */
    public void replaceGroupEntries(Set<Tuple3<EntityKind, Long, Set<AppGroupEntry>>> entriesForGroups) {

        Set<Tuple2<Long, Set<AppGroupEntry>>> appEntriesToUpdate = entriesForGroups
                .stream()
                .filter(d -> d.v1.equals(EntityKind.APPLICATION))
                .map(Tuple3::skip1)
                .collect(Collectors.toSet());

        Set<Tuple2<Long, Set<AppGroupEntry>>> initiativeEntriesToUpdate = entriesForGroups
                .stream()
                .filter(d -> d.v1.equals(EntityKind.CHANGE_INITIATIVE))
                .map(Tuple3::skip1)
                .collect(Collectors.toSet());

        appGroupEntryDao.replaceGroupApplicationEntries(appEntriesToUpdate);
        appGroupEntryDao.replaceGroupChangeInitiativeEntries(initiativeEntriesToUpdate);
    }


    public void synchGroupEntries(Set<Tuple3<EntityKind, Long, Set<EntityReference>>> entriesForGroups, String userId) {
        Set<Long> groupIds = SetUtilities.map(entriesForGroups, Tuple3::v2);
        Map<Long, List<EntityReference>> existingEntitiesByGroupId = appGroupEntryDao.fetchEntitiesForGroups(groupIds);

        Set<Tuple2<Long, EntityReference>> removals = new HashSet<>();
        Set<Tuple2<Long, EntityReference>> additions = new HashSet<>();
        entriesForGroups
                .forEach(t -> {
                    Set<EntityReference> relevantExistingEntities = SetUtilities.filter(
                            existingEntitiesByGroupId.get(t.v2),
                            d -> d.kind() == t.v1);
                    DiffResult<EntityReference> diff = mkDiff(relevantExistingEntities, t.v3);
                    removals.addAll(SetUtilities.map(
                            diff.waltzOnly(),
                            d -> tuple(t.v2, d)));
                    additions.addAll(SetUtilities.map(
                            diff.otherOnly(),
                            d -> tuple(t.v2, d)));
                });

        LOG.debug("Additions: {}, Removals: {}", additions.size(), removals.size());

        appGroupDao.processAdditionsAndRemovals(additions, removals, userId);

        int changeLogCount = summarizeResults(Stream
                .concat(additions.stream().map(t -> t.concat(Operation.ADD)),
                        removals.stream().map(t -> t.concat(Operation.REMOVE)))
                .map(t -> (ChangeLog) ImmutableChangeLog
                        .builder()
                        .message(format(t.v3 == Operation.ADD
                                        ? "%s: '%s' added to group"
                                        : "%s: '%s' removed from group",
                                t.v2.kind().prettyName(),
                                t.v2.name().orElse("(id:" + t.v2.id() + ")")))
                        .operation(t.v3)
                        .parentReference(mkRef(EntityKind.APP_GROUP, t.v1))
                        .childKind(t.v2.kind())
                        .childId(t.v2.id())
                        .userId(userId)
                        .severity(Severity.INFORMATION)
                        .build())
                .collect(Collectors.collectingAndThen(
                        Collectors.toSet(),
                        changeLogService::write)));

        LOG.info(
                "Synchronized groups: {} additions, {} removals, {} change log entries created",
                additions.size(),
                removals.size(),
                changeLogCount);
    }


    public void verifyUserCanUpdateGroup(String userId, long groupId) throws InsufficientPrivelegeException {
        if (!appGroupMemberDao.canUpdate(groupId, userId)) {
            throw new InsufficientPrivelegeException(userId + " cannot update group: " + groupId);
        }
    }


    private EntityRelationship buildChangeInitiativeRelationship(String username, long groupId, long changeInitiativeId) {
        EntityReference appGroupRef = ImmutableEntityReference.builder()
                .kind(EntityKind.APP_GROUP)
                .id(groupId)
                .build();

        EntityReference changeInitiativeRef = ImmutableEntityReference.builder()
                .kind(EntityKind.CHANGE_INITIATIVE)
                .id(changeInitiativeId)
                .build();

        return ImmutableEntityRelationship.builder()
                .a(appGroupRef)
                .b(changeInitiativeRef)
                .relationship(RelationshipKind.RELATES_TO.name())
                .lastUpdatedBy(username)
                .provenance("waltz")
                .build();
    }


    private void audit(long groupId, String userId, String message, EntityKind childKind, Long childId, Operation operation) {
        changeLogService.write(ImmutableChangeLog.builder()
                .message(message)
                .userId(userId)
                .parentReference(ImmutableEntityReference.builder().id(groupId).kind(EntityKind.APP_GROUP).build())
                .childKind(Optional.ofNullable(childKind))
                .childId(Optional.ofNullable(childId))
                .operation(operation)
                .build());
    }
}
