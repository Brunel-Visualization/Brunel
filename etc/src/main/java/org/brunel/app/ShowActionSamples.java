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

package org.brunel.app;

import org.brunel.action.Action;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.io.CSV;
import org.brunel.model.VisItem;
import org.brunel.model.VisSingle;
import org.brunel.util.WebDisplay;

import java.awt.*;
import java.io.InputStream;
import java.util.Scanner;

class ShowActionSamples {

    private static final Object[][] TEST_DATA = new Object[][]{{"animal", "size", "mammal", "aquatic"},
            {"fish", 1, "no", "yes"}, {"cat", 3, "yes", "no"},
            {"bat", 2, "yes", "no"}, {"dolphin", 4, "yes", "yes"}, {"elephant", 5, "yes", "no"},
            {"frog", 1.5, "no", "yes"}, {"crocodile", 4.5, "no", "no"}};

    private static final Dataset baseball, simple, cpi, whiskey;

    public static void main(String[] args) throws Exception {

        Example[] examples = new Example[]{
                new Example(simple, "bar x(animal) y(size) color(animal) sort(size) filter(size, #row, animal, mammal)"),
                new Example(simple, "bar x(animal) y(size) color(mammal) transpose"),
                new Example(simple, "bar x(aquatic, mammal) y(size) color(mammal)"),
                new Example(simple, "bar x(mammal) y(size) color(aquatic) stack label(#all) tooltip(aquatic, #count)"),
                new Example(whiskey, "bar x(country) y(#count) color(category) stack"),
                new Example(whiskey, "bar x(country) y(#count) percent(#count) color(category) stack"),
                new Example(whiskey, "bar x(country, category) y(#count) color(category) sort(#count) tooltip(#all)"),
                new Example(cpi, "bar x(housing) bin(housing) y(#count)"),
                new Example(cpi, "line x(date) y(food)"),
                new Example(cpi, "line x(date) y(food, housing) color(#series) tooltip(#series)"),
                new Example(simple, "line x(animal) y(size) color(mammal) transpose"),
                new Example(simple, "line x(animal) y(size) color(mammal)"),
                new Example(simple, "area x(animal) y(size)"),
                new Example(simple, "area x(animal) y(size) color(mammal) stack"),
                new Example(whiskey, "area y(#count) x(country) color(category) stack"),
                new Example(whiskey, "area y(#count) percent(#count) x(country) color(category) stack"),
                new Example(cpi, "area x(date) yrange(food, housing)"),
                new Example(simple, "bar y(size) color(animal) stack polar label(#all)"),
                new Example(simple, "line y(size) x(animal) polar"),
                new Example(whiskey, "bar y(#count) color(country) stack polar"),
                new Example(whiskey, "point x(Price) y(Rating) color(category) label(Price)"),
                new Example(whiskey,
                        "point x(Price) y(Rating) color(country) tooltip('(', Price, ',' , Rating, ')') style('size:40px; stroke-width:6; symbol:poly-5; fill-opacity:0.2')"),
                new Example(whiskey, "edge yrange(country, category) chord size(#count) color(country) tooltip(#all)"),
                new Example(whiskey, "point size(#count) color(country) bubble label(country, '\\n', #count) tooltip(#all)"),
                new Example(baseball, "point size(SlugRate) color(Salary) bubble"),
                new Example(simple,
                        "bar x(mammal, aquatic) size(#count) color(#count) label('Mammal=', mammal, '\\nAquatic=', aquatic) treemap tooltip(#all)"),
                new Example(whiskey,
                        "bar x(country, category) size(#count) mean(Rating) color(Rating) label(Country, ',' , category) treemap tooltip(#all)"),
                new Example(baseball, "bar x(HomeRunRate) y(Salary)"),
                new Example(baseball, "bar x(HomeRunRate) y(Salary) transpose"),
                new Example(baseball,
                        "point x(PutOutRate) y(AssistRate) color(SlugRate) size(salary) sort(salary) style('size:200%') tooltip(#all)"),
                new Example(cpi, "point x(food) y(housing) bin(housing, food) color(#count) style('symbol:rect; size:95%; border-radius:5')"),
                new Example(cpi,
                        "bar x(date) bin(date) mean(housing) y(housing) style('fill:#ff0000') | line x(date) y(food) style('stroke:#00dd00')"),
                new Example(cpi, "x(date) y(housing) + line x(date) y(food) mean(food) using(interpolate)"),
                new Example(cpi, "bar x(date) bin(date) mean(housing) y(housing) style('fill:red') + line x(date) y(food)"),
                new Example(whiskey,
                        "point size(#count) x(country) bubble > point size(Rating) color(category) bubble label(Rating)"),
                new Example(cpi,
                        "bar x(date) bin(date) mean(housing) size(housing) label(date) style('fill-opacity:0; outline:#68932F') style('label { fill:white; font-size:16; align:end; valign:start; pad:3}') treemap >> line x(date) y(housing)"),
                new Example(whiskey,
                        "bar x(Rating) bin(Rating) y(#count) style('size:80%; fill:#222222') transpose >> bar x(category) color(country) treemap"),
                new Example(whiskey,
                        "bar x(country) split(country) size(#count) label(country) style('element {fill:navy} label { fill:white; font-size:12; align:start; valign:start; pad:2}') treemap >> bar y(#count) color(category) split(country) stack polar")
        };

        WebDisplay d3 = new WebDisplay("ShowActionSamples");

        for (Example example : examples) {
            System.out.println(example.name + ": " + example.actionText);
            Action action = Action.parse(example.actionText);
            VisItem item = action.apply(example.data);
            VisSingle single = item.getSingle();
            String name = "point";
            if (single.tElement != null) name = single.tElement.name();
            if (single.tDiagram != null) name = "diagram";
            if (item.children() != null) name = "composite";
            d3.buildOneOfMultiple(item, name, example.actionText, new Dimension(700, 500));
        }
        d3.showInBrowser();
    }

    private static Field[] readResourceAsCSV(String resourceName) {
        InputStream stream = ShowActionSamples.class.getResourceAsStream("/org/brunel/app/data-csv/" + resourceName);
        Scanner scanner = new Scanner(stream).useDelimiter("\\A");
        String s = scanner.hasNext() ? scanner.next() : "";
        return CSV.read(s);
    }

    static class Example {
        final Dataset data;
        final String name;
        final String actionText;

        public Example(Dataset data, String actionText) {
            this.data = data;
            this.actionText = actionText;
            int p = actionText.indexOf(' ');
            name = actionText.substring(0, p);
        }

    }

    static {
        simple = Dataset.make(CSV.makeFields(ShowActionSamples.TEST_DATA));
        baseball = Dataset.make(readResourceAsCSV("baseball2004.csv"));
        cpi = Dataset.make(readResourceAsCSV("UK_CPI.csv"));
        whiskey = Dataset.make(readResourceAsCSV("whiskey.csv"));
    }
}
