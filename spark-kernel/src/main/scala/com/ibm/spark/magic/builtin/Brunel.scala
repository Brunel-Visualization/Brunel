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

class Brunel extends CellMagic with IncludeInterpreter {

  override def execute(code: String) = {
    val line = if (code contains "%%") {
      code.replace("\n", " ")
    } else {
      code
    }


    var width: Int = 500
    var height: Int = 400
    var action: String = null
    var extras: String = null
    var dataFrameNames: Array[String] = null
    var data: Option[DataFrame] = None

    val parts = line.split("::")
    if (parts.length > 2) {
      throw new IllegalArgumentException("Only one ':' allowed in brunel magic. Format is 'ACTION : key=value, ...'")
    }

    if (parts.length > 0) {
      action = parts(0).trim
      dataFrameNames = org.brunel.scala.Brunel.getDatasetNames(action)
      if (dataFrameNames.length > 0) cacheDataFrames(dataFrameNames);
    }

    if (parts.length > 1) {

      extras = parts(1).trim
      val dataName = findTerm("data", extras, null)
      if (dataName != null) {
        try {
          data = getData(dataName)
          if (!data.nonEmpty) throw new IllegalArgumentException("Requested DataFrame: " + dataName + " was not found.")
        } catch {
          case e: Exception => e.printStackTrace
        }
      }
      width = findTerm("width", extras, width.toString).toInt
      height = findTerm("height", extras, height.toString).toInt
    }

    val visId = "visid" + java.util.UUID.randomUUID.toString
    val controlsId = "controlsId" + java.util.UUID.randomUUID.toString
    val brunelOutput = org.brunel.scala.Brunel.create(data.orNull, action, width, height, visId, controlsId)
    val version = org.brunel.scala.Brunel.options.version
    val jsloc = org.brunel.scala.Brunel.options.locJavaScript
    val d3loc = org.brunel.scala.Brunel.options.locD3
    val topoJsonLoc = org.brunel.scala.Brunel.options.locTopoJson

    val d3js =  brunelOutput.js
    val d3dynamicCss = brunelOutput.css

    val html =

      s"""
         <link rel="stylesheet" type="text/css" href="$jsloc/brunel.$version.css" charset="utf-8">
         <link rel="stylesheet" type="text/css" href="$jsloc/sumoselect.css" charset="utf-8">
         <style> $d3dynamicCss </style>
         <div id="$controlsId" class="brunel"/>
         |<svg id="$visId" width="$width" height="$height"></svg>
         |
         |<script>
         |require.config({
            waitSeconds: 60,
            paths: {
                'd3': '$d3loc',
                'topojson' : '$topoJsonLoc',
                'brunel' : '$jsloc/brunel.$version.min',
                'brunelControls' : '$jsloc/brunel.controls.$version.min'
            },

            shim: {
               'brunel' : {
                    exports: 'BrunelD3',
                    deps: ['d3', 'topojson'],
                    init: function() {
                       return {
                         BrunelD3 : BrunelD3,
                         BrunelData : BrunelData
                      }
                    }
                },
               'brunelControls' : {
                    exports: 'BrunelEventHandlers',
                    init: function() {
                       return {
                         BrunelEventHandlers: BrunelEventHandlers,
                         BrunelJQueryControlFactory: BrunelJQueryControlFactory
                      }
                    }
                }

            }

        });

        require(["d3"], function(d3) {
        require(["brunel", "brunelControls"], function(brunel, brunelControls) {

            $d3js
            ""
        });
        });
        </script>""".stripMargin

    CellMagicOutput(MIMEType.TextHtml -> html)
  }

  def cacheDataFrames(datasetNames: Array[String]) = {
    for (name <- datasetNames) {
      val data = getData(name)
      if (data.nonEmpty) {
        org.brunel.scala.Brunel.cacheData(name, data.get)
      }
    }
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
    default
  }

  def getData(dataFrameName: String): Option[DataFrame] = {
    interpreter.read(dataFrameName) match {
      case Some(df) =>
        Some(df.asInstanceOf[DataFrame])
      case _ =>
        None
    }
  }

}
