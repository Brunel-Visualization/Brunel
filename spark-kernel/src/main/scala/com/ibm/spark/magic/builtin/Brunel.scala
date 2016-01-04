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

package com.ibm.spark.magic.builtin

import com.ibm.spark.kernel.protocol.v5.MIMEType
import com.ibm.spark.magic.dependencies.IncludeInterpreter
import com.ibm.spark.magic.{CellMagic, CellMagicOutput}
import org.apache.spark.sql.DataFrame
import org.brunel.util.D3Integration.createBrunelJSON
import scala.util.parsing.json.JSON.parseFull

class Brunel extends CellMagic with IncludeInterpreter {

  override def execute(code: String) = {
    val line = if (code contains "%%") {
      code.replace("\n", " ")
    } else {
      code
    }

    var data: Option[String] = None
    var width: Int = 500
    var height: Int = 400
    var action: String = null
    var extras: String = null
    val parts = line.split("::")

    if (parts.length > 2) {
      throw new IllegalArgumentException("Only one ':' allowed in brunel magic. Format is 'ACTION : key=value, ...'")
    }

    if (parts.length > 1) {
      action = parts(0).trim
      extras = parts(1).trim
      val dataName = findTerm("data", extras, "")
      if (dataName != null) {
        try {
          data = getData(dataName)
        } catch {
          case e: Exception => e.printStackTrace
        }
      }
      width = findTerm("width", extras, width.toString).toInt
      height = findTerm("height", extras, height.toString).toInt
    }

    val visId = "visid" + java.util.UUID.randomUUID.toString
    val json = createBrunelJSON(data.getOrElse(throw new IllegalArgumentException("No dataset provided")), action, width, height, visId)

    val jsonMap = parseFull(json)
    val d3js = jsonMap match {
      case Some(m: Map[String, Any]) => m("js") match {
        case s: String => s
      }
    }
    val d3dynamicCss = jsonMap match {
      case Some(m: Map[String, Any]) => m("css") match {
        case s: String => s
      }
    }
    val d3staticCss = """.brunel * {
                  |    font-family: helvetica neue, helvetica, arial, sans-serif;
                  |    font-size: 12px;
                  |}
                  |
                  |.brunel .background {
                  |    fill: white;
                  |    padding: 5px;
                  |}
                  |
                  |.brunel .coordinates {
                  |    opacity: 0
                  |}
                  |
                  |.brunel .title {
                  |    font-weight: bold;
                  |    font-size: 14px;
                  |    padding: 2px;
                  |}
                  |
                  |.brunel .box {
                  |    fill: #f9ecb7;
                  |    stroke: #666666;
                  |}
                  |
                  |.brunel .axis {
                  |    fill: #888888
                  |}
                  |
                  |.brunel .domain {
                  |    fill: none;
                  |    stroke: #888888
                  |}
                  |
                  |.brunel .tick line {
                  |    stroke: #888888
                  |}
                  |
                  |.brunel .tick mark {
                  |    font-size: 12px;
                  |}
                  |
                  |.brunel .grid {
                  |    visibility: hidden;
                  |}
                  |
                  |.brunel .legend.title {
                  |    fill: #00669e;
                  |}
                  |
                  |.brunel .legend.label {
                  |    font-size: 12px;
                  |}
                  |
                  |.brunel .legend rect {
                  |    stroke: black;
                  |    stroke-width: 1px;
                  |    stroke-opacity: 0.5
                  |}
                  |
                  |.brunel .hierarchy {
                  |    fill: white;
                  |    stroke-width: 0;
                  |}
                  |
                  |.brunel .hierarchy .L0 {
                  |    visibility: hidden;
                  |}
                  |
                  |.brunel .hierarchy .L1 {
                  |    fill: #f9ecb7;
                  |}
                  |
                  |.brunel .hierarchy .L2 {
                  |    fill: #d3870d;
                  |}
                  |
                  |.brunel .treemap.axis {
                  |    visibility: hidden
                  |}
                  |
                  |.brunel .treemap.axis .L1 {
                  |    visibility: visible;
                  |    fill: #d3870d;
                  |    font-weight: bold;
                  |    font-size: 14px
                  |}
                  |
                  |.brunel .treemap.axis .L2 {
                  |    visibility: visible;
                  |    fill: white;
                  |    font-size: 10px
                  |}
                  |
                  |.brunel .tree .edge {
                  |    stroke-width: 1;
                  |    stroke: #0b9bdb;
                  |    fill: none;
                  |}
                  |
                  |.brunel .label {
                  |    fill: black;
                  |    font-size: 12px;
                  |    stroke: none;
                  |    pointer-events: none;
                  |}
                  |
                  |.brunel .labels text {
                  |    text-shadow: 1px 1px 5px white, -1px -1px 5px white, -1px 1px 5px white, 1px -1px 5px white
                  |}
                  |
                  |.brunel .element.text {
                  |    text-anchor: middle
                  |}
                  |
                  |.brunel .element.line, .brunel .element.path, .brunel .element.edge {
                  |    stroke-width: 2px;
                  |    stroke: #0b9bdb;
                  |    fill: none;
                  |}
                  |
                  |.brunel .element.bar, .brunel .element.polygon, .brunel .element.area, .brunel .element.line.filled, .brunel .element.path.filled {
                  |    fill-rule: nonzero;
                  |    fill: #0b9bdb;
                  |    stroke-width: 1px;
                  |    stroke: #000000;
                  |    stroke-opacity: 0.5;
                  |}
                  |
                  |.brunel .element.point, .brunel .chord .element.edge {
                  |    fill: #0b9bdb;
                  |    fill-opacity: 0.8;
                  |    stroke: #000000
                  |}
                  |
                  |.brunel circle.element, .brunel .chord .element.edge {
                  |    stroke-width: 1px;
                  |    stroke-opacity:0.25
                  |}
                  |
                  |.brunel rect.point, .brunel .chord .element.edge {
                  |    stroke-width: 0;
                  |}
                  |
                  |.brunel .element2 .element, .brunel .element2 .element.filled {
                  |    fill: #f2574c
                  |}
                  |
                  |.brunel .element3 .element, .brunel .element2 .element.filled {
                  |    fill: #67b51b
                  |}
                  |
                  |.brunel .element2 .line, .brunel .element2 .path, .brunel .element2 .edge {
                  |    fill: none;
                  |    stroke: #f2574c
                  |}
                  |
                  |.brunel .element3 .line, .brunel .element3 .path, .brunel .element3 .edge {
                  |    fill: none;
                  |    stroke: #67b51b
                  |}
                  |
                  |.brunel .element.polygon.nondata {fill:#f2f2f2; stroke:#c0c0c0}
                  |
                  |.brunel .horizontal-box {
                  |    -moz-box-orient: horizontal;
                  |    -moz-box-align: stretch;
                  |    display: flex;
                  |    flex-direction: row;
                  |    align-items: stretch;
                  |    box-sizing: border-box;
                  |    padding-top: 5px;
                  |    padding-bottom: 5px
                  |}
                  |
                  |.brunel .control-label {
                  |    min-width: 5em;
                  |    padding-right: 10px;
                  |    padding-left: 10px
                  |}
                  |
                  |.brunel .control-left-label {
                  |    text-align: right;
                  |}
                  |
                  |.brunel .control-title {
                  |    padding-right: 10px;
                  |}
                  |
                  |.brunel.tooltip, .brunel.tooltip .title, .brunel.tooltip .field {
                  |    font-size: 12px;
                  |    color: white;
                  |}
                  |
                  |div.brunel.tooltip {
                  |    padding: 0.5em;
                  |    background: black;
                  |    opacity: 0.8;
                  |    border-radius: 3px;
                  |    position: absolute;
                  |    pointer-events: none;
                  |    visibility: hidden;
                  |}
                  |
                  |div.brunel.tooltip:before {
                  |    content: '\25b2';
                  |    position: absolute;
                  |    display: inline;
                  |    left: 0;
                  |    top: 0;
                  |    width: 100%;
                  |    color: black;
                  |    margin-top: -10px;
                  |    font-size: 12px;
                  |    line-height: 1;
                  |    text-align: center;
                  |}
                  |
                  |div.brunel.tooltip:after {
                  |    content: '\25bc';
                  |    position: absolute;
                  |    display: inline;
                  |    left: 0;
                  |    top: 100%;
                  |    width: 100%;
                  |    color: black;
                  |    margin-top: -2px;
                  |    font-size: 12px;
                  |    line-height: 1;
                  |    text-align: center;
                  |}
                  |
                  |div.brunel.tooltip.above:before {
                  |   visibility: hidden;
                  |}
                  |
                  |div.brunel.tooltip.below:after {
                  |    visibility: hidden;
                  |}
                  |
                  |.brunel.tooltip .title {
                  |    font-weight: bold;
                  |}
                  |
                  |.brunel.tooltip .field {
                  |    color: #fdb813
                  |}
                  |
                  |.brunel rect.overlay {
                  |    cursor: move;
                  |    fill: none;
                  |    pointer-events: all;
                  |}
                  |
                  |.ui-slider {
                  |    width: 200px;
                  |}
                  |"""
    val d3css = d3staticCss.concat(d3dynamicCss)
    val html =
      s"""<style> $d3css </style>
         |<svg id="$visId" width="$width" height="$height"></svg>
         |
         |<script>
         |require.config({
            waitSeconds: 60,
            paths: {
                'd3': '//cdnjs.cloudflare.com/ajax/libs/d3/3.5.5/d3.min',
                'BrunelD3': 'https://rawgit.com/Brunel-Visualization/Brunel/v0.8/python/brunel/brunel_ext/BrunelD3',
                'BrunelData': 'https://rawgit.com/Brunel-Visualization/Brunel/v0.8/python/brunel/brunel_ext/BrunelData',
                'BrunelEventHandlers': 'https://rawgit.com/Brunel-Visualization/Brunel/v0.8/python/brunel/brunel_ext/BrunelEventHandlers'
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
                },
            }

        });

        require(["d3"], function(d3) {
        require(["BrunelD3", "BrunelData", "BrunelEventHandlers"], function(BrunelD3, BrunelData, BrunelEventHandlers) {
            $d3js
            ""
        });
        });
        </script>""".stripMargin

    CellMagicOutput(MIMEType.TextHtml -> html)
  }

  def findTerm(key: String, str: String, default: String): String = {
    for (expr <- str.split(",")) {
      val terms = expr.split("=")
      if (terms.length != 2) {
        throw new IllegalArgumentException("Bad format for key=value pair: " + expr)
      }
      if (key == terms(0).trim.toLowerCase) {
        return terms(1).trim
      }
    }
    default.toString
  }

  def getData(dataFrameName: String): Option[String] = {
    interpreter.read(dataFrameName) match {
      case Some(df) =>
        val serialized = serialize(df.asInstanceOf[DataFrame])
        Some(serialized)
      case _ =>
        None
    }
  }

  def serialize(df: DataFrame): String = {
    val csv = df.columns.mkString(",") ++ "\n" ++ df.collect().map(_.mkString(",")).mkString("\n")
    csv
  }
}
