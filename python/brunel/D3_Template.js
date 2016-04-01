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
        'd3': '//cdnjs.cloudflare.com/ajax/libs/d3/3.5.5/d3.min',
        'BrunelD3': '/nbextensions/brunel_ext/BrunelD3',
        'BrunelData': '/nbextensions/brunel_ext/BrunelData',
        'BrunelEventHandlers': '/nbextensions/brunel_ext/BrunelEventHandlers'
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

require(["d3"], function (d3) {
    require(["BrunelD3", "BrunelData", "BrunelEventHandlers"], function (BrunelD3, BrunelData, BrunelEventHandlers) {
        {{d3js}}
        {{controls}}
    });
});
