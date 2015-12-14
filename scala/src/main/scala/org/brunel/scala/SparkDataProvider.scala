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

package org.brunel.scala

import org.apache.spark.sql.Row

import org.brunel.data.values.Provider
import org.brunel.data.values.ColumnProvider
import org.brunel.data.Data

import java.util.HashMap

//Brunel Provider implementation for Spark DataFrames typed to the expected data value types needed by Brunel.
class
SparkDataProvider[+T] (colIndex:Int, rows:Array[Row]) extends Provider {

  //Column structure created on-demand when needed to estimate memory
  lazy val column : List[T]  = List.tabulate(rows.length) (x => rows(x).getAs[T](colIndex) )

  def compareRows(a: Int, b: Int, categoryOrder: HashMap[Object,Integer]): Int = {
    val p = rows(a).getAs[T](colIndex)
    val q = rows(b).getAs[T](colIndex)
    if (p == q) return 0
    if (p == null) return 1
    if (categoryOrder.isEmpty()) {
      return Data.compare(p,q)
    }
    else {
      return categoryOrder.get(p) - categoryOrder.get(q)
    }
  }

  def count(): Int = rows.length
  def expectedSize(): Int = {

    val unique = column.distinct
    var total = 24 + 4 * column.length
    if (column(0).isInstanceOf[String]) {
      for (item <- unique) total += (42 + item.asInstanceOf[String].length() * 2)
    }
    else {
      total += (16*unique.length)
    }

    return total

  }

  def setValue(o: Any,index: Int): Provider = ColumnProvider.copy(this).setValue(o, index);
  def value(index: Int): Object = rows(index).getAs(colIndex)
}
