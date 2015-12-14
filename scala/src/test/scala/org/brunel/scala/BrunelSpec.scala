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

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import org.apache.spark._
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.types.DoubleType

@RunWith(classOf[JUnitRunner])
class BrunelSpec extends UnitSpec {

  //Spark configuration
  val conf = new SparkConf().setAppName("BrunelSpec").setMaster("local")
  val spark = new SparkContext(conf)
  val sqlContext = new SQLContext(spark)
  import sqlContext.implicits._
  import org.apache.spark.sql.functions.to_date

  val df_orig = spark.makeRDD(Array(
    (123, "234.2", "2007-12-12", "Ford"),
    (123, "247.5", "2007-12-12", "Chevy"),
    (189, "254", "2007-12-13", "Audi"),
    (187, "missing", "2007-12-12", "Porsche"))).toDF("mpg", "horsepower", "date", "name")

  //Column type conversion
  val df = df_orig.withColumn("date", to_date(df_orig("date"))).withColumn("horsepower", df_orig("horsepower").cast(DoubleType))
  val rows = df.collect()

  "The SparkDataProvider columns" should "have proper length and expected memory size" in {
    val dp0: SparkDataProvider[Int] = new SparkDataProvider[Int](0, rows)
    val dp1: SparkDataProvider[Double] = new SparkDataProvider[Double](1, rows)
    val dp3: SparkDataProvider[String] = new SparkDataProvider[String](3, rows)

    assert(dp0.count() == 4)
    assert(dp1.count() == 4)
    assert(dp0.expectedSize() == 88)
    assert(dp0.value(3) == 187)
    assert(dp1.value(3) == null)
    assert(dp3.expectedSize() == 248)
    assert(dp3.value(3) == "Porsche")
  }


  "The Dataset" should "contain numeric, date and String fields" in {
    val dataset = Brunel.makeDataset(df)
    assert(dataset.fields.length == 7)     //4 defined fields + 3 synthetic
    assert(dataset.field("mpg").isNumeric())
    assert(dataset.field("horsepower").isNumeric())
    assert(!dataset.field("name").isNumeric())
    assert(dataset.field("date").isDate())

    assert(dataset.field("horsepower").value(3) == null)
    assert(dataset.field("mpg").value(3) == 187)
    assert(dataset.field("name").value(3) == "Porsche")

  }

  "A BrunelOutput" should "contain javascript and css" in {

    val brunelOutput = Brunel.create(df, "x(mpg) y(horsepower) style('fill:red')", 600, 600, "visid")
    assert(brunelOutput.js != null)
    assert(brunelOutput.css.length() > 0)
    assert(brunelOutput.js.contains("['mpg', 'horsepower'],[123.0, 234.2],[123.0, 247.5],[189.0, 254.0],[187.0, null]"))
    assert(brunelOutput.css.contains("fill: red;"))

  }

}
