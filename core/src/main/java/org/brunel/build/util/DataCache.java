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
import org.brunel.data.io.CSV;

import java.io.IOException;
import java.io.InputStream;

/**
 * Brunel's access to Datasets via a cache.  The key must be unique.  If the key is an URL it will
 * be used to load the content if it is not in the cache.
 */
public class DataCache {

    // Local cache is needed because Brunel needs identical Datasets to be the same instance
    private static DatasetCache localCache = new SimpleCache();
    private static DatasetCache userCache =null;


    /**
     * Specify an alternative cache implementation for storing Datasets by key.
     * This should called once before any use of caching.
     * @param cache the alternate cache to use
     */
    public static synchronized void useCache(DatasetCache cache) {
    	userCache = cache;
    }

    /**
     * This method will return the value in the cache if it exists, and if not, it will read the data
     *
     * @param dataKey the location to read from
     * @return the built data set
     */
    public static synchronized Dataset get(String dataKey) throws IOException {
        return get(dataKey, null);
    }

    /**
     * This method will return the value in the cache if it exists, and if not, it will read the data.
     * If the input stream is defined it will be used, otherwise the datakey is assumed to be a URL
     *
     * @param dataKey the location to read from
     * @param is      a stream to read from -- may be null.
     * @return the built data set
     * @throws IOException can happen if the key is not a URL and the content for the key is no longer in the cache
     */
    public static synchronized Dataset get(String dataKey, InputStream is) throws IOException {
        if (dataKey == null) return null;

        Dataset dataset = localCache.retrieve(dataKey);
        
        //Not found in local cache check if in user supplied cache.
        //If so, stick it back in the local cache
        if (dataset == null && userCache != null) {
        	dataset = userCache.retrieve(dataKey);
        	localCache.store(dataKey, dataset);
        }
        
        if (dataset == null) {
        	String content = is == null ? ContentReader.readContentFromUrl(dataKey) : ContentReader.readContent(is);
            dataset = Dataset.make(CSV.read(content));
            localCache.store(dataKey, dataset);
            if (userCache != null) userCache.store(dataKey, dataset);
        }
        return dataset;
    }



}
