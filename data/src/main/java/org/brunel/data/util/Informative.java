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

package org.brunel.data.util;

import org.brunel.data.Data;

import java.util.HashMap;
import java.util.Map;

public class Informative {
	protected Map<String, Object> info = new HashMap<>();    // Stores the info

	/**
	 * Copy properties form a source.
	 * Note that missing values in the source are made missing in this also
	 *
	 * @param source where to get the values
	 * @param items  the keys to copy over
	 */
	public void copyProperties(Informative source, String... items) {
		for (String s : items) set(s, source.property(s));
	}

	public void copyAllProperties(Informative other) {
		info.putAll(other.info);
	}

	public Integer intProperty(String key) {
		Double v = Data.asNumeric(property(key));
		return v == null ? null : v.intValue();
	}

	public Double numProperty(String key) {
		return Data.asNumeric(property(key));
	}

	public Object property(String key) {
		return info.get(key);
	}

	public String strProperty(String key) {
		Object v = property(key);
		return v == null ? null : v.toString();
	}

	public boolean isProperty(String key) {
		Boolean v = (Boolean) property(key);
		return v != null && v;
	}

	public void set(String key, Object value) {
		if (value == null)
			info.remove(key);
		else
			info.put(key, value);
	}
}
