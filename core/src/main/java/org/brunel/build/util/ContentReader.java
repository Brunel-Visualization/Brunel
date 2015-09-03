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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class ContentReader {
    public static String readContent(InputStream is) throws IOException {
        // Use StringBuilder to read the data in large chunks
        StringBuilder builder = new StringBuilder();
        byte[] data = new byte[20480];

        int c;
        while ((c = is.read(data, 0, data.length)) > 0)
            builder.append(new String(data, 0, c));

        return builder.toString();
    }

    public static String readContentFromUrl(String url) throws IOException {

		//TODO:  Centrally handle security
    	return readContent( new URL(url).openStream());
    }
}
