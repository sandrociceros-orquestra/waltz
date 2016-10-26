import angular from "angular";
import {buildHierarchies, initialiseData} from "../common";


const initialState = {
    dataTypes: [],
    trees: []
};


function controller($state,
                    dataTypes,
                    staticPanelStore,
                    svgStore,
                    lineageStore) {

    const vm = initialiseData(this, initialState);

    vm.trees = buildHierarchies(dataTypes);

    vm.nodeSelected = (node) => vm.selectedNode = node;

    svgStore
        .findByKind('DATA_TYPE')
        .then(xs => vm.diagrams = xs);

    staticPanelStore
        .findByGroup("HOME.DATA-TYPE")
        .then(panels => vm.panels = panels);

    vm.blockProcessor = b => {
        b.block.onclick = () => $state.go('main.data-type.code', { code: b.value });
        angular.element(b.block).addClass('clickable');
    };


    vm.flowTableInitialised = (api) => {
        vm.exportLineageReports = api.export;
    }


    lineageStore
        .findAllContributions()
        .then(lineageReports => vm.lineageReports = lineageReports);

}


controller.$inject = [
    '$state',
    'dataTypes',
    'StaticPanelStore',
    'SvgDiagramStore',
    'PhysicalFlowLineageStore'
];


const view = {
    template: require('./data-type-list.html'),
    controllerAs: 'ctrl',
    controller
};


export default view;