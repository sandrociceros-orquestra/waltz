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

package org.finos.waltz.service.role;

import org.finos.waltz.common.SetUtilities;
import org.finos.waltz.data.user.UserRoleDao;
import org.finos.waltz.model.role.RoleView.ImmutableRoleView;
import org.finos.waltz.model.role.RoleView.RoleView;
import org.finos.waltz.model.user.User;
import org.finos.waltz.schema.tables.records.RoleRecord;
import org.finos.waltz.data.role.RoleDao;
import org.finos.waltz.model.role.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

import static org.finos.waltz.common.Checks.checkNotEmpty;

@Service
public class RoleService {

    private static final Logger LOG = LoggerFactory.getLogger(RoleService.class);

    private final RoleDao roleDao;
    private final UserRoleDao userRoleDao;

    @Autowired
    public RoleService(RoleDao roleDao,
                       UserRoleDao userRoleDao) {
        this.roleDao = roleDao;
        this.userRoleDao = userRoleDao;
    }

    public Long create(String key, String roleName, String description) {
        checkNotEmpty(roleName, "role name cannot be empty");
        checkNotEmpty(key, "key cannot be empty");
        LOG.info("creating new role: {}", roleName);

        RoleRecord role = new RoleRecord();
        role.setKey(key);
        role.setName(roleName);
        role.setDescription(description);
        role.setIsCustom(true);

        return roleDao.create(role);
    }

    public Set<Role> findAllRoles() {
       return roleDao.findAllRoles();
    }

    public RoleView getRoleView(Long id) {
        Set<User> users = userRoleDao.findUsersForRole(id);
        Role role = roleDao.getRoleById(id);

        return ImmutableRoleView
                .builder()
                .role(role)
                .users(SetUtilities.map(users, User::userName))
                .build();
    }
}
