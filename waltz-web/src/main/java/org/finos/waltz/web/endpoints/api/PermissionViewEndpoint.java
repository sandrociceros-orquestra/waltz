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


import org.finos.waltz.service.permission.PermissionViewService;
import org.finos.waltz.web.endpoints.Endpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.finos.waltz.web.WebUtilities.mkPath;
import static org.finos.waltz.web.endpoints.EndpointUtilities.getForList;


@Service
public class PermissionViewEndpoint implements Endpoint {

    private static final String BASE_URL = mkPath("api", "permission-view");
    private final PermissionViewService permissionViewService;


    @Autowired
    public PermissionViewEndpoint(PermissionViewService permissionViewService) {
        this.permissionViewService = permissionViewService;
    }


    @Override
    public void register() {
        getForList(BASE_URL, (req, resp) -> permissionViewService.findAll());
    }

}
