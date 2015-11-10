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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A very simple and limited cache that stores a Dataset instance content by a key name.
 *
 * @author drope
 */
public class SimpleCache implements DatasetCache {

    /* Maximum amount of memory to allow in cache in bytes */
    private static final long MAX_ESTIMATED_MEMORY = 512 * 1024 * 1024;

    private final Map<String, Dataset> map = Collections.synchronizedMap(new MapCache());
    private long estimatedMemoryUse = 0;

    @Override
    public synchronized void store(String key, Dataset dataset) {
        Dataset previous = map.put(key, dataset);
        if (previous != null) estimatedMemoryUse -= previous.expectedSize();
        estimatedMemoryUse += dataset.expectedSize();
    }

    @Override
    public synchronized Dataset retrieve(String key) {
        return map.get(key);
    }

    private class MapCache extends LinkedHashMap<String, Dataset> {
        protected boolean removeEldestEntry(Map.Entry<String, Dataset> eldest) {
            synchronized (SimpleCache.this) {
                if (estimatedMemoryUse > MAX_ESTIMATED_MEMORY) {
                    // This will be removed, so reduce the total memory size
                    estimatedMemoryUse -= eldest.getValue().expectedSize();
                    return true;
                } else {
                    return false;
                }
            }
        }
    }
}
