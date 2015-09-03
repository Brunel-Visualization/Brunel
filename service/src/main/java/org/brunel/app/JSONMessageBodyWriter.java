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

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import com.google.gson.Gson;

/**
 * So we can write IBM JSON objects
 */
@Provider
@Produces(MediaType.APPLICATION_JSON)

public class JSONMessageBodyWriter<T> implements MessageBodyWriter<T> {

	private final Gson gson = new Gson();

    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mt) {
        return true;
    }

    public long getSize(T t, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    public void writeTo(T gsonArtifact, Class<?> aClass, Type type,
                        Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> multivaluedMap,
                        OutputStream os) throws IOException, WebApplicationException {


    	//Return as UTF-8 (for python parsing)
    	String json =  gson.toJson(gsonArtifact);
    	byte[] utf8json = json.getBytes("UTF8");
	    os.write(utf8json);

    }

}
