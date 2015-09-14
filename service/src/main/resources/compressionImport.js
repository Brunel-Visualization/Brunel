/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/*
 * When using compression, the compression code assuems that all symbols
 * can be renamed. However when we use symbols from another library, that
 * is not the case. Standard DOM symbols are not compressed away, but D3
 * calls are, so the following ensures those symbols are kept intact.
 *
 * Note that calls from the generated code are fine; it is only calls that
 * are made through the compressed BrunelD3 and BrunelData code that needs
 * to note which names not to compress.
 */

function attr(){}
function text(){}
function style(){}
function selectAll(){}
function data(){}
function append(){}
function enter(){}
function exit(){}
function transition(){}
function duration(){}
function tween(){}
function node(){}

// These are SVG calls, which may not be needed, but are added just to be sure
function createSVGPoint(){}
function matrixTransform(){}
function getScreenCTM(){}
