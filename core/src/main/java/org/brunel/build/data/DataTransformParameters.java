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

package org.brunel.build.data;

/**
 * This structure is defined by the base builder and passed to abstract data building methods of that class.
 * The data is actually transformed as part of the building step, and so no action is needed if only the transformed
 * data is desired. However, a builder may want to build structures or calls that allow the data to be re-processed,
 * for example when animation or filtering occur, and these commands will allow that to be re-done later.
 */
public class DataTransformParameters {

    /**
     * Command to add constant fields to the data
     */
    public final String constantsCommand;
    /**
     * Command to filter the data
     */
    public final String filterCommand;
    /**
     * Command to extract multiple values from a field's text
     */
    public final String eachCommand;
    /**
     * Command to transform the data without aggregation (bin, rank, inner, outer)
     */
    public final String transformCommand;
    /**
     * Command to aggregate the data
     */
    public final String summaryCommand;
    /**
     * Command to stack the data
     */
    public final String stackCommand;
    /**
     * Command to sort data and data categories
     */
    public final String sortCommand;
    /**
     * Command to sort data only
     */
    public final String sortRowsCommand;
    /**
     * Command to transform to a series
     */
    public final String seriesCommand;
    /**
     * Command to retain only used fields
     */
    public final String usedCommand;

    /**
     * Initialize all the relevant parameters
     */
    public DataTransformParameters(String constantsCommand, String filterCommand, String eachCommand, String binCommand, String summaryCommand,
                                   String stackCommand, String sortCommand, String sortRowsCommand, String seriesCommand, String usedCommand) {
        this.constantsCommand = constantsCommand;
        this.filterCommand = filterCommand;
        this.eachCommand = eachCommand;
        this.transformCommand = binCommand;
        this.summaryCommand = summaryCommand;
        this.stackCommand = stackCommand;
        this.sortCommand = sortCommand;
        this.sortRowsCommand = sortRowsCommand;
        this.seriesCommand = seriesCommand;
        this.usedCommand = usedCommand;
    }

}
