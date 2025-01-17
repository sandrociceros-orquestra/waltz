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

import {remote} from "./remote";

export function mkDataTypeStore() {

    const findAll = (force= false) => {
        return remote.fetchAppList(
            "GET",
            "api/data-types",
            null,
            {force});
    };

    const getById = (id, force = false) => {
        return remote.fetchAppData(
            "GET",
            `api/data-types/id/${id}`,
            null,
            {},
            {force})
    };

    const findByParentId = (id, force = false) => {
        return remote.fetchAppData(
            "GET",
            `api/data-types/parent-id/${id}`,
            null,
            {},
            {force})
    };

    const findSuggestedByRef = (ref, force = false) => {
        return remote.fetchViewList(
            "GET",
            `api/data-types/suggested/entity/${ref.kind}/${ref.id}`,
            null,
            {force});
    };

    const migrate = (sourceDataTypeId, targetDataTypeId, removeSource = false) => {
        return remote.execute(
            "POST",
            `api/data-types/migrate/${sourceDataTypeId}/to/${targetDataTypeId}?removeSource=${removeSource}`,
            null);
    };

    return {
        findAll,
        getById,
        findByParentId,
        findSuggestedByRef,
        migrate
    };
}

export const dataTypeStore = mkDataTypeStore();
