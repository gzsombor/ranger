/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ranger.services.kms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ranger.authorization.hadoop.config.RangerConfiguration;
import org.apache.ranger.plugin.model.RangerPolicy;
import org.apache.ranger.plugin.model.RangerService;
import org.apache.ranger.plugin.model.RangerServiceDef;
import org.apache.ranger.plugin.service.RangerBaseService;
import org.apache.ranger.plugin.service.ResourceLookupContext;
import org.apache.ranger.services.kms.client.KMSResourceMgr;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class RangerServiceKMS extends RangerBaseService {

	private static final Log LOG = LogFactory.getLog(RangerServiceKMS.class);

	public static final String ACCESS_TYPE_DECRYPT_EEK    = "decrypteek";
	public static final String ACCESS_TYPE_GENERATE_EEK   = "generateeek";
	public static final String ACCESS_TYPE_GET_METADATA   = "getmetadata";

	public RangerServiceKMS() {
		super();
	}
	
	@Override
	public void init(RangerServiceDef serviceDef, RangerService service) {
		super.init(serviceDef, service);
	}

	@Override
	public Map<String,Object> validateConfig() throws Exception {
		Map<String, Object> ret = new HashMap<String, Object>();
		String 	serviceName  	    = getServiceName();
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> RangerServiceKMS.validateConfig Service: (" + serviceName + " )");
		}
		if ( configs != null) {
			try  {
				ret = KMSResourceMgr.validateConfig(serviceName, configs);
			} catch (Exception e) {
				LOG.error("<== RangerServiceKMS.validateConfig Error:" + e);
				throw e;
			}
		}
		if(LOG.isDebugEnabled()) {
			LOG.debug("<== RangerServiceKMS.validateConfig Response : (" + ret + " )");
		}
		return ret;
	}

	@Override
	public List<String> lookupResource(ResourceLookupContext context) throws Exception {
		
		List<String> ret 		   = new ArrayList<String>();
		String 	serviceName  	   = getServiceName();
		Map<String,String> configs = getConfigs();
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> RangerServiceKMS.lookupResource Context: (" + context + ")");
		}
		if (context != null) {
			try {
				ret  = KMSResourceMgr.getKMSResources(serviceName,configs,context);
			} catch (Exception e) {
			  LOG.error( "<==RangerServiceKMS.lookupResource Error : " + e);
			  throw e;
			}
		}
		if(LOG.isDebugEnabled()) {
			LOG.debug("<== RangerServiceKMS.lookupResource Response: (" + ret + ")");
		}
		return ret;
	}

	@Override
	public List<RangerPolicy> getDefaultRangerPolicies() {

		if (LOG.isDebugEnabled()) {
			LOG.debug("==> RangerServiceKMS.getDefaultRangerPolicies() ");
		}

		List<RangerPolicy> ret = super.getDefaultRangerPolicies();

		String adminPrincipal = RangerConfiguration.getInstance().get(ADMIN_USER_PRINCIPAL);
		String adminKeytab = RangerConfiguration.getInstance().get(ADMIN_USER_KEYTAB);
		String authType = RangerConfiguration.getInstance().get(RANGER_AUTH_TYPE,"simple");

		String adminUser = getLookupUser(authType, adminPrincipal, adminKeytab);

		// Add default policies for HDFS & HIVE users.
		List<RangerServiceDef.RangerAccessTypeDef> hdfsAccessTypeDefs = new ArrayList<RangerServiceDef.RangerAccessTypeDef>();
		List<RangerServiceDef.RangerAccessTypeDef> hiveAccessTypeDefs = new ArrayList<RangerServiceDef.RangerAccessTypeDef>();

		for(RangerServiceDef.RangerAccessTypeDef accessTypeDef : serviceDef.getAccessTypes()) {
			if (accessTypeDef.getName().equalsIgnoreCase(ACCESS_TYPE_GET_METADATA)) {
				hdfsAccessTypeDefs.add(accessTypeDef);
				hiveAccessTypeDefs.add(accessTypeDef);
			} else if (accessTypeDef.getName().equalsIgnoreCase(ACCESS_TYPE_GENERATE_EEK)) {
				hdfsAccessTypeDefs.add(accessTypeDef);
			} else if (accessTypeDef.getName().equalsIgnoreCase(ACCESS_TYPE_DECRYPT_EEK)) {
				hiveAccessTypeDefs.add(accessTypeDef);
			}
		}

		for (RangerPolicy defaultPolicy : ret) {

			List<RangerPolicy.RangerPolicyItem> policyItems = defaultPolicy.getPolicyItems();
			for (RangerPolicy.RangerPolicyItem item : policyItems) {
				List<String> users = item.getUsers();
                                if(StringUtils.isNotBlank(adminUser)){
                                        users.add(adminUser);
                                }
				item.setUsers(users);
			}

			String hdfsUser = RangerConfiguration.getInstance().get("ranger.kms.service.user.hdfs", "hdfs");
			if (hdfsUser != null && !hdfsUser.isEmpty()) {
				LOG.info("Creating default KMS policy item for " + hdfsUser);
				List<String> users = new ArrayList<String>();
				users.add(hdfsUser);
				RangerPolicy.RangerPolicyItem policyItem = createDefaultPolicyItem(hdfsAccessTypeDefs, users);
				policyItems.add(policyItem);
			}


			String hiveUser = RangerConfiguration.getInstance().get("ranger.kms.service.user.hive", "hive");

			if (hiveUser != null && !hiveUser.isEmpty()) {
				LOG.info("Creating default KMS policy item for " + hiveUser);
				List<String> users = new ArrayList<String>();
				users.add(hiveUser);
				RangerPolicy.RangerPolicyItem policyItem = createDefaultPolicyItem(hiveAccessTypeDefs, users);
				policyItems.add(policyItem);
			}
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== RangerServiceKMS.getDefaultRangerPolicies() : " + ret);
		}
		return ret;
	}

	private RangerPolicy.RangerPolicyItem createDefaultPolicyItem(List<RangerServiceDef.RangerAccessTypeDef> accessTypeDefs, List<String> users) {

		if (LOG.isDebugEnabled()) {
			LOG.debug("==> RangerServiceTag.createDefaultPolicyItem()");
		}

		RangerPolicy.RangerPolicyItem policyItem = new RangerPolicy.RangerPolicyItem();

		policyItem.setUsers(users);

		List<RangerPolicy.RangerPolicyItemAccess> accesses = new ArrayList<RangerPolicy.RangerPolicyItemAccess>();

		for (RangerServiceDef.RangerAccessTypeDef accessTypeDef : accessTypeDefs) {
			RangerPolicy.RangerPolicyItemAccess access = new RangerPolicy.RangerPolicyItemAccess();
			access.setType(accessTypeDef.getName());
			access.setIsAllowed(true);
			accesses.add(access);
		}

		policyItem.setAccesses(accesses);
		policyItem.setDelegateAdmin(true);

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== RangerServiceTag.createDefaultPolicyItem(): " + policyItem );
		}
		return policyItem;
	}
}

