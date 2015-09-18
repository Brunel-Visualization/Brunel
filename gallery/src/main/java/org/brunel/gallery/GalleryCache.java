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
package org.brunel.gallery;

import org.brunel.build.util.DatasetCache;
import org.brunel.data.Dataset;


import com.ibm.websphere.objectgrid.ObjectGridException;
import com.ibm.websphere.objectgrid.ObjectMap;
import com.ibm.websphere.objectgrid.Session;
import com.ibm.websphere.objectgrid.UndefinedMapException;
import com.ibm.websphere.objectgrid.plugins.TransactionCallbackException;

/**
 *	Implementation of a Brunel DatasetCache that uses the Bluemix Data Cache service. 
 *
 */
public class GalleryCache implements DatasetCache {


	@Override
	public void store(String key, Dataset dataset) {
		
		try {
			Session ogSession = GridConnection.getObjectGrid().getSession();
			ObjectMap map = ogSession.getMap(GridConnection.MAP_NAME);
			map.upsert(key, dataset);
			
		} catch (TransactionCallbackException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (ObjectGridException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}


	}

	@Override
	public Dataset retrieve(String key) {

		try {
			Session ogSession = GridConnection.getObjectGrid().getSession();
			ObjectMap map = ogSession.getMap(GridConnection.MAP_NAME);
			map = ogSession.getMap(GridConnection.MAP_NAME);
			Dataset d = (Dataset) map.get(key);
			if (d == null)
				return null;
			try {
				return d;
			}
			catch (Exception e) {
				map.remove(key);
				return null;
			}
		} catch (UndefinedMapException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (ObjectGridException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

}
