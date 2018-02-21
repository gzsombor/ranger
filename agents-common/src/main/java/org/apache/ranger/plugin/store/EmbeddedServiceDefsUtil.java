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

import java.io.InputStream;
import java.io.InputStreamReader;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ranger.plugin.model.RangerServiceDef;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/*
 * This utility class deals with service-defs embedded in ranger-plugins-common
 * library (hdfs/hbase/hive/knox/storm/..). If any of these service-defs
 * don't exist in the given service store, they will be created in the store
 * using the embedded definitions.
 *
 * init() method should be called from ServiceStore implementations to
 * initialize embedded service-defs.
 */
public class EmbeddedServiceDefsUtil {
	private static final Log LOG = LogFactory.getLog(EmbeddedServiceDefsUtil.class);


	public static final String EMBEDDED_SERVICEDEF_TAG_NAME  = "tag";
	public static final String EMBEDDED_SERVICEDEF_HDFS_NAME  = "hdfs";
	public static final String EMBEDDED_SERVICEDEF_HBASE_NAME = "hbase";
	public static final String EMBEDDED_SERVICEDEF_HIVE_NAME  = "hive";
	public static final String EMBEDDED_SERVICEDEF_KMS_NAME   = "kms";
	public static final String EMBEDDED_SERVICEDEF_KNOX_NAME  = "knox";
	public static final String EMBEDDED_SERVICEDEF_STORM_NAME = "storm";
	public static final String EMBEDDED_SERVICEDEF_YARN_NAME  = "yarn";
	public static final String EMBEDDED_SERVICEDEF_KAFKA_NAME = "kafka";
	public static final String EMBEDDED_SERVICEDEF_SOLR_NAME  = "solr";
	public static final String EMBEDDED_SERVICEDEF_NIFI_NAME  = "nifi";
	public static final String EMBEDDED_SERVICEDEF_ATLAS_NAME  = "atlas";
	public static final String EMBEDDED_SERVICEDEF_WASB_NAME  = "wasb";
	public static final String EMBEDDED_SERVICEDEF_SQOOP_NAME = "sqoop";
	public static final String EMBEDDED_SERVICEDEF_KYLIN_NAME  = "kylin";

	public static final String PROPERTY_CREATE_EMBEDDED_SERVICE_DEFS = "ranger.service.store.create.embedded.service-defs";

	public static final String HDFS_IMPL_CLASS_NAME  = "org.apache.ranger.services.hdfs.RangerServiceHdfs";
	public static final String HBASE_IMPL_CLASS_NAME = "org.apache.ranger.services.hbase.RangerServiceHBase";
	public static final String HIVE_IMPL_CLASS_NAME  = "org.apache.ranger.services.hive.RangerServiceHive";
	public static final String KMS_IMPL_CLASS_NAME   = "org.apache.ranger.services.kms.RangerServiceKMS";
	public static final String KNOX_IMPL_CLASS_NAME  = "org.apache.ranger.services.knox.RangerServiceKnox";
	public static final String STORM_IMPL_CLASS_NAME = "org.apache.ranger.services.storm.RangerServiceStorm";
	public static final String YARN_IMPL_CLASS_NAME  = "org.apache.ranger.services.yarn.RangerServiceYarn";
	public static final String KAFKA_IMPL_CLASS_NAME = "org.apache.ranger.services.kafka.RangerServiceKafka";
	public static final String SOLR_IMPL_CLASS_NAME  = "org.apache.ranger.services.solr.RangerServiceSolr";
	public static final String NIFI_IMPL_CLASS_NAME  = "org.apache.ranger.services.nifi.RangerServiceNiFi";
	public static final String ATLAS_IMPL_CLASS_NAME  = "org.apache.ranger.services.atlas.RangerServiceAtlas";

	private static EmbeddedServiceDefsUtil instance = new EmbeddedServiceDefsUtil();

	private Gson gsonBuilder;

	/** Private constructor to restrict instantiation of this singleton utility class. */
	private EmbeddedServiceDefsUtil() {
		gsonBuilder = new GsonBuilder().setDateFormat("yyyyMMdd-HH:mm:ss.SSS-Z").setPrettyPrinting().create();
	}

	public static EmbeddedServiceDefsUtil instance() {
		return instance;
	}


	public RangerServiceDef getEmbeddedServiceDef(String defType) throws Exception {
		RangerServiceDef serviceDef=null;
		if(StringUtils.isNotEmpty(defType)){
			serviceDef=loadEmbeddedServiceDef(defType);
		}
		return serviceDef;
	}


	private RangerServiceDef loadEmbeddedServiceDef(String serviceType) throws Exception {
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> EmbeddedServiceDefsUtil.loadEmbeddedServiceDef(" + serviceType + ")");
		}

		RangerServiceDef ret = null;
	
		String resource = "/service-defs/ranger-servicedef-" + serviceType + ".json";

		InputStream inStream = getClass().getResourceAsStream(resource);

		InputStreamReader reader = new InputStreamReader(inStream);

		ret = gsonBuilder.fromJson(reader, RangerServiceDef.class);

		if(LOG.isDebugEnabled()) {
			LOG.debug("==> EmbeddedServiceDefsUtil.loadEmbeddedServiceDef(" + serviceType + ")");
		}

		return ret;
	}

}
