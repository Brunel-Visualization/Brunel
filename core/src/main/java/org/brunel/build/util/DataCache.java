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

package org.brunel.build.util;

import org.brunel.data.Dataset;
import org.brunel.data.io.CSV;
import org.brunel.util.GeneratedData;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * Brunel's access to Datasets via a cache.  The key must be unique.  If the key is an URL it will
 * be used to load the content if it is not in the cache.
 */
public class DataCache {

    public static final String SAMPLE_DATA_LOCATION = "http://brunel.mybluemix.net/sample_data/";
    // Local cache is needed because Brunel needs identical Datasets to be the same instance
    private static final DatasetCache localCache = new SimpleCache();
    private static DatasetCache userCache;

    /**
     * Specify an alternative cache implementation for storing Datasets by key.
     * This should called once before any use of caching.
     *
     * @param cache the alternate cache to use
     */
    public static synchronized void useCache(DatasetCache cache) {
        userCache = cache;
    }

    /**
     * Store a dataset into the cache.  If a user cache is provided, the data will be stored there as well.
     *
     * @param dataKey unique identifier for data
     * @param data    the data to cache
     */
    public static synchronized void store(String dataKey, Dataset data) {
        localCache.store(dataKey, data);
        if (userCache != null) {
            userCache.store(dataKey, data);
        }
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
     * @param dataKey the location to read from.  This may be a URL or a UUID for uploaded data.
     * @param is      a stream to read from -- may be null.
     * @return the built data set
     * @throws IOException can happen if the key is not a URL and the content for the key is no longer in the cache
     */
    public static synchronized Dataset get(String dataKey, InputStream is) throws IOException {
        if (dataKey == null) return null;

        boolean useCache = true;                                    // Unless we ask to refresh, use it!

        URI uri = makeURI(dataKey);
        if (dataKey.startsWith("generate:")) {
            String content = dataKey.substring(0, "generate:".length()).trim();
            Dataset data = GeneratedData.make(content);
            localCache.store(dataKey, data);
        } else if (dataKey.startsWith("raw:")) {
            // Raw data is simply a CSV file with newlines replaced by semi-colons. This is intended for quick
            // testing and not as a production facility -- complex CSV will likely fail.
            Dataset data = Dataset.make(CSV.read(dataKey.substring(4).replaceAll(";", "\n")));
            localCache.store(dataKey, data);
        } else if (uri != null && uri.getScheme() != null) {
            // We change our URI
            if (uri.getScheme().equals("sample"))
                uri = makeURI(SAMPLE_DATA_LOCATION + uri.getSchemeSpecificPart());
            else if (uri.getScheme().equals("refresh")) {
                uri = makeURI(uri.toString().replace("refresh", "http"));
                useCache = false;
            }
        }

        Dataset dataset = useCache ? localCache.retrieve(dataKey) : null;
        if (dataset != null) return dataset;

        // Not found in local cache check if in user supplied cache.
        // If so, stick it back in the local cache
        if (userCache != null && useCache) {
            dataset = userCache.retrieve(dataKey);
            if (dataset != null) localCache.store(dataKey, dataset);
        }
        if (dataset != null) return dataset;

        // Actually read the data
        String content = is == null ? ContentReader.readContentFromUrl(uri) : ContentReader.readContent(is);
        dataset = Dataset.make(CSV.read(content));
        localCache.store(dataKey, dataset);
        if (userCache != null) userCache.store(dataKey, dataset);
        return dataset;
    }

    /* Returns null for invalid URIs */
    private static URI makeURI(String key) {
        try {
            key = key.replaceAll(" ", "%20");
            return new URI(key);
        } catch (Exception e) {
            return null;
        }
    }

}
