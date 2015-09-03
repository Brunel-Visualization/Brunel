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

package org.brunel.build.util;

import org.brunel.data.Dataset;

/**
 * Interface defining a caching mechanism to cache Dataset instances by a key name. Implementations
 * can be set using DataCache.useCache().
 * @author drope
 *
 */
public interface DatasetCache {

	/**
	 * Store a given data set using the given key
	 * @param key unique key
	 * @param dataset the Dataset instance to store in the cache
	 */
	public void store(String key, Dataset dataset);

	/**
	 * Retrieve a Dataset instance given a key
	 * @param key the key
	 * @return the Dataset instance for the given key
	 */
	public Dataset retrieve(String key);


}
