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

import org.brunel.data.Dataset;
import org.brunel.model.VisSingle;

/**
 * This is a dataset that is the result of a transform from another data set.
 * It keeps a copy of the original data and the transform parameters used by it.
 */
public class TransformedData extends Dataset {

	private final Dataset source;                           	// Original dataset the transform was applied to
	private final TransformParameters transformParameters;     	// The parameters used for the transform

	public static TransformedData make(VisSingle vis) {
		TransformParameters params = new TransformParameterBuilder(vis).make();
		Dataset source = vis.getDataset();
		return new TransformedData(source, params, transform(source, params));
	}

	/**
	 * Private constructor copies all the input from the result data (shallow copy) and stores info
	 *
	 * @param params parameters used to transform
	 * @param result transformed data
	 */
	private TransformedData(Dataset source, TransformParameters params, Dataset result) {
		super(result.fields, result);
		this.transformParameters = params;
		this.source = source;
	}

	public static Dataset transform(Dataset data, TransformParameters params) {
		return data
				.addConstants(params.constantsCommand)                              // add constant fields
				.each(params.eachCommand)                                           // divide up fields into parts
				.filter(params.filterCommand)                                       // filter data
				.transform(params.transformCommand)                                 // bin, rank, ... on data
				.summarize(params.summaryCommand)                                   // summarize data
				.series(params.seriesCommand)                                       // convert series
				.setRowCount(params.rowCountCommand)                                // set the number of rows
				.sort(params.sortCommand)                                           // sort data
				.sortRows(params.sortRowsCommand)                                   // sort rows only
				.stack(params.stackCommand);                                        // stack data
	}

	public Dataset getSource() {
		return source;
	}

	public TransformParameters getTransformParameters() {
		return transformParameters;
	}
}
