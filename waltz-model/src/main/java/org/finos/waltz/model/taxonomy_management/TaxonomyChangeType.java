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

package org.finos.waltz.model.taxonomy_management;

public enum TaxonomyChangeType {

    ADD_PEER,
    ADD_CHILD,
    DEPRECATE,
    MOVE,
    MERGE,
    REMOVE,
    REORDER_SIBLINGS,
    UPDATE_NAME,
    UPDATE_DESCRIPTION,
    UPDATE_CONCRETENESS,
    UPDATE_EXTERNAL_ID,
    BULK_ADD,
    BULK_RESTORE,
    BULK_UPDATE

}
