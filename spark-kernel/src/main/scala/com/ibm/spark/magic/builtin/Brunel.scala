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

    var data: Option[DataFrame] = None
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
    val brunelOutput = org.brunel.scala.Brunel.create(data.getOrElse(throw new IllegalArgumentException("No dataset provided")), action, width, height, visId)

    val d3js =  brunelOutput.js 
    val d3dynamicCss = brunelOutput.css 

    val html =
      
      s"""
         <link rel="stylesheet" type="text/css" href="http://brunelvis.org/js/brunel.0.9.css" charset="utf-8">
         <link rel="stylesheet" type="text/css" href="http://brunelvis.org/js/sumoselect.css" charset="utf-8">
         <style> $d3dynamicCss </style>
         |<svg id="$visId" width="$width" height="$height"></svg>
         |
         |<script>
         |require.config({
            waitSeconds: 60,
            paths: {
                'd3': '//cdnjs.cloudflare.com/ajax/libs/d3/3.5.5/d3.min',
                'brunel' : 'http://brunelvis.org/js/brunel.0.9.min',
                'brunelControls' : 'http://brunelvis.org/js/brunel.controls.0.9.min'
            },

            shim: {
               'brunel' : {
                    exports: 'BrunelD3',
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

  def getData(dataFrameName: String): Option[DataFrame] = {
    interpreter.read(dataFrameName) match {
      case Some(df) =>
        Some(df.asInstanceOf[DataFrame])
      case _ =>
        None
    }
  }

}
