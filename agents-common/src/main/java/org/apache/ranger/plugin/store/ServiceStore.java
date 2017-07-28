/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.plugin.store;

import java.util.List;
import java.util.Map;

import org.apache.ranger.plugin.model.RangerPolicy;
import org.apache.ranger.plugin.model.RangerSecurityZone;
import org.apache.ranger.plugin.model.RangerService;
import org.apache.ranger.plugin.model.RangerServiceDef;
import org.apache.ranger.plugin.service.RangerServiceException;
import org.apache.ranger.plugin.util.SearchFilter;
import org.apache.ranger.plugin.util.ServicePolicies;

public interface ServiceStore {

    String OPTION_FORCE_RENAME = "forceRename";

	void init();

	RangerServiceDef createServiceDef(RangerServiceDef serviceDef) throws RangerServiceException;

	RangerServiceDef updateServiceDef(RangerServiceDef serviceDef) throws RangerServiceException;

	void deleteServiceDef(Long id, Boolean forceDelete) throws RangerServiceException;

	void updateTagServiceDefForAccessTypes() throws RangerServiceException;

	RangerServiceDef getServiceDef(Long id) throws RangerServiceException;

	RangerServiceDef getServiceDefByName(String name);

	List<RangerServiceDef> getServiceDefs(SearchFilter filter);

	PList<RangerServiceDef> getPaginatedServiceDefs(SearchFilter filter);

	RangerService createService(RangerService service) throws RangerServiceException;

	RangerService updateService(RangerService service, Map<String, Object> options) throws RangerServiceException;

	void deleteService(Long id) throws RangerServiceException;

	RangerService getService(Long id) throws RangerServiceException;

	RangerService getServiceByName(String name) throws RangerServiceException;

	List<RangerService> getServices(SearchFilter filter);

	PList<RangerService> getPaginatedServices(SearchFilter filter);

	RangerPolicy createPolicy(RangerPolicy policy) throws RangerServiceException;

	RangerPolicy updatePolicy(RangerPolicy policy) throws RangerServiceException;

	void deletePolicy(RangerPolicy policy, RangerService service) throws RangerServiceException;

	void deletePolicy(RangerPolicy policy) throws RangerServiceException;

	RangerPolicy getPolicy(Long id) throws RangerServiceException;

	List<RangerPolicy> getPolicies(SearchFilter filter) throws RangerServiceException;

	Long getPolicyId(final Long serviceId, final String policyName, final Long zoneId);

	PList<RangerPolicy> getPaginatedPolicies(SearchFilter filter) throws RangerServiceException;

	List<RangerPolicy> getPoliciesByResourceSignature(String serviceName, String policySignature, Boolean isPolicyEnabled) throws RangerServiceException;

	List<RangerPolicy> getServicePolicies(Long serviceId, SearchFilter filter) throws RangerServiceException;

	PList<RangerPolicy> getPaginatedServicePolicies(Long serviceId, SearchFilter filter) throws RangerServiceException;

	List<RangerPolicy> getServicePolicies(String serviceName, SearchFilter filter) throws RangerServiceException;

	PList<RangerPolicy> getPaginatedServicePolicies(String serviceName, SearchFilter filter) throws RangerServiceException;

	ServicePolicies getServicePoliciesIfUpdated(String serviceName, Long lastKnownVersion, boolean needsBackwardCompatibility) throws RangerServiceException;

	Long getServicePolicyVersion(String serviceName);

	ServicePolicies getServicePolicyDeltasOrPolicies(String serviceName, Long lastKnownVersion) throws RangerServiceException;

	ServicePolicies getOnlyServicePolicyDeltas(String serviceName, Long lastKnownVersion) throws RangerServiceException;

	RangerPolicy getPolicyFromEventTime(String eventTimeStr, Long policyId);

	void setPopulateExistingBaseFields(Boolean populateExistingBaseFields);

	Boolean getPopulateExistingBaseFields();

    RangerSecurityZone getSecurityZone(Long id) throws Exception;

    RangerSecurityZone getSecurityZone(String name) throws Exception;
}
