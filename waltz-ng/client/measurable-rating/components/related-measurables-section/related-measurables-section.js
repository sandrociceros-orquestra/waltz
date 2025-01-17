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

import {initialiseData} from "../../../common";
import {CORE_API} from "../../../common/services/core-api-utils";

import template from "./related-measurables-section.html";
import {mkSelectionOptions} from "../../../common/selector-utils";


/**
 * @name waltz-related-measurables-explorer
 *
 * @description
 * This component ...
 */
const bindings = {
    parentEntityRef: "<"
};


const initialState = {
    hasImplicitlyRelatedMeasurables: false
};

function controller($q, serviceBroker) {
    const vm = initialiseData(this, initialState);

    vm.$onChanges = () => {

        if (!vm.parentEntityRef) {
            return;
        }

        const selectionOptions = mkSelectionOptions(vm.parentEntityRef);

        serviceBroker
            .loadViewData(CORE_API.MeasurableRatingStore.hasMeasurableRatings, [selectionOptions])
            .then(r => vm.hasImplicitlyRelatedMeasurables = r.data);
    };
}


controller.$inject = [
    "$q",
    "ServiceBroker"
];


const component = {
    template,
    bindings,
    controller
};


export default {
    component,
    id: "waltzRelatedMeasurablesSection"
};
