/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

require.config({
    waitSeconds: 60,
    paths: {
        'd3': '{{d3loc}}',
        'topojson': '{{topojsonloc}}',
        'BrunelD3': '{{jsloc}}/BrunelD3',
        'BrunelData': '{{jsloc}}/BrunelData',
        'BrunelEventHandlers': '{{jsloc}}/BrunelEventHandlers'
    },
    shim: {
        'BrunelD3': {
            exports: 'BrunelD3'
        },
        'BrunelData': {
            exports: 'BrunelData'
        },
        'BrunelEventHandlers': {
            exports: 'BrunelEventHandlers'
        }

    }

});

require(["d3", "BrunelD3", "BrunelData", "BrunelEventHandlers"], function(d3, BrunelD3, BrunelData, BrunelEventHandlers ) {
    {{d3js}}
});
