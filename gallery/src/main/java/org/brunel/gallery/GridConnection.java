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

import com.ibm.websphere.objectgrid.ClientClusterContext;
import com.ibm.websphere.objectgrid.ObjectGrid;
import com.ibm.websphere.objectgrid.ObjectGridException;
import com.ibm.websphere.objectgrid.ObjectGridManager;
import com.ibm.websphere.objectgrid.ObjectGridManagerFactory;
import com.ibm.websphere.objectgrid.security.config.ClientSecurityConfiguration;
import com.ibm.websphere.objectgrid.security.config.ClientSecurityConfigurationFactory;
import com.ibm.websphere.objectgrid.security.plugins.builtins.UserPasswordCredentialGenerator;
/**
 * Connections to Bluemix Data Cache service grid.
 *
 */
public class GridConnection {
	private static ObjectGrid objectGrid = null;
	public static final String MAP_NAME = "BRUNEL.LAT.O";

	/**
	 * Gets the object grid.  Local if no VCAP_SERVICES, Bluemix cache otherwise.
	 * @return the ObjectGrid for the cache's map
	 */
	public static ObjectGrid getObjectGrid() {

		if (objectGrid == null) objectGrid = VCAPServices.isVCAPSerivces() ? getRemoteGrid() : getLocalGrid();
		return objectGrid;
	}

	//Cache on Bluemix
	private static ObjectGrid getRemoteGrid() {
		try {
	
			if (objectGrid == null) {
				ObjectGridManager ogm = ObjectGridManagerFactory
						.getObjectGridManager();
				ClientSecurityConfiguration csc = null;
				csc = ClientSecurityConfigurationFactory
						.getClientSecurityConfiguration();
				csc.setCredentialGenerator(new UserPasswordCredentialGenerator(
						VCAPServices.dataCacheUserName(), VCAPServices.dataCachePassword()));
				csc.setSecurityEnabled(true);
		
				ClientClusterContext ccc = ogm.connect(
						VCAPServices.dataCacheCatalogEndPoint(), csc, null);
				objectGrid = ogm.getObjectGrid(ccc,
						VCAPServices.dataCacheGridName());
			}
			return objectGrid;
		} catch (ObjectGridException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	
	}
	
	//Cache on local machine
	private static ObjectGrid getLocalGrid() {
	
		try {
			if (objectGrid == null) {
				ObjectGridManager ogm = ObjectGridManagerFactory
						.getObjectGridManager();
				objectGrid = ogm.getObjectGrid("Brunelb");
				if (objectGrid == null) {
					objectGrid = ogm.createObjectGrid("Brunelb");
					objectGrid.defineMap(MAP_NAME);
					objectGrid.initialize();
				}
			}
			return objectGrid;
		} catch (ObjectGridException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	
	}

}
