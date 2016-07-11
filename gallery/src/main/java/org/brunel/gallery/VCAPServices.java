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

import java.io.IOException;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;

/**
 * Credential information for Bluemix Services
 *
 */
public class VCAPServices {

	private static JSONObject vcapServices = null;
	
	public static boolean isVCAPSerivces() {
		return getVcapServices() != null;
	}

	public static String dataCachePassword() {
		return VCAPServices.getString(VCAPServices.getDataCacheCredentials(), "password");
	}

	public static String dataCacheCatalogEndPoint() {
		return VCAPServices.getString(VCAPServices.getDataCacheCredentials(), "catalogEndPoint");
	}

	public static String dataCacheUserName() {
		return VCAPServices.getString(VCAPServices.getDataCacheCredentials(), "username");
	}
	
	public static String dataCacheGridName() {
		return VCAPServices.getString(VCAPServices.getDataCacheCredentials(), "gridName");
	}
	
	private static JSONObject getDataCacheCredentials() {
		return getCredentials("DataCache");
	}
	

	private static JSONObject getCredentials(String serviceName) {
		final JSONObject services = VCAPServices.getVcapServices();
		JSONArray serviceArray = (JSONArray)services.get(serviceName);
		
		//Doubt we'd use more than one instance of a given service 
		if (serviceArray.size()>0) {
			JSONObject service = (JSONObject)serviceArray.get(0);
			return (JSONObject)service.get("credentials");
		}

		System.err.println("Service " + serviceName + " not found in VCAP_Services");
		return null;

	}

	private static String getString(JSONObject obj, String s) {
		return (String)obj.get(s);
	}

	private static JSONArray getUserProvided() {
		final JSONObject services = VCAPServices.getVcapServices();
		return (JSONArray)services.get("user-provided");
	}

	private static JSONObject getVcapServices() {
		if (VCAPServices.vcapServices == null) {
			final String vcapString = System.getenv("VCAP_SERVICES");
			if (vcapString == null) return null;

			try {
				VCAPServices.vcapServices = JSONObject.parse(vcapString);
			} catch (final IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return VCAPServices.vcapServices;
	}


}
