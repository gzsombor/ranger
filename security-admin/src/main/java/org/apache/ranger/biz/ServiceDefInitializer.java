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

package org.apache.ranger.biz;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.ranger.authorization.hadoop.config.RangerConfiguration;
import org.apache.ranger.plugin.model.RangerServiceDef;
import org.apache.ranger.plugin.store.EmbeddedServiceDefsUtil;
import static org.apache.ranger.plugin.store.EmbeddedServiceDefsUtil.*;
import org.apache.ranger.plugin.store.ServiceStore;
import org.apache.ranger.plugin.util.ServiceDefUtil;

/**
 * Class to initialize service definitions in the database from the embedded
 * json files.
 *
 */
public class ServiceDefInitializer {
    private static final Logger LOG = Logger.getLogger(ServiceDefInitializer.class);

    // following servicedef list should be reviewed/updated whenever a new
    // embedded service-def is added
    private static final String DEFAULT_BOOTSTRAP_SERVICEDEF_LIST = "tag,hdfs,hbase,hive,kms,knox,storm,yarn,kafka,solr,atlas,nifi,sqoop,kylin";
    private static final String PROPERTY_SUPPORTED_SERVICE_DEFS = "ranger.supportedcomponents";
    private Set<String> supportedServiceDefs;

    private boolean createEmbeddedServiceDefs = true;
    private ServiceStore store;
    private RangerConfiguration config;

    private RangerServiceDef hdfsServiceDef;
    private RangerServiceDef hBaseServiceDef;
    private RangerServiceDef hiveServiceDef;
    private RangerServiceDef kmsServiceDef;
    private RangerServiceDef knoxServiceDef;
    private RangerServiceDef stormServiceDef;
    private RangerServiceDef yarnServiceDef;
    private RangerServiceDef kafkaServiceDef;
    private RangerServiceDef solrServiceDef;
    private RangerServiceDef nifiServiceDef;
    private RangerServiceDef atlasServiceDef;
    private RangerServiceDef wasbServiceDef;
    private RangerServiceDef sqoopServiceDef;
    private RangerServiceDef kylinServiceDef;

    private RangerServiceDef tagServiceDef;

    public long getHdfsServiceDefId() {
        return getId(hdfsServiceDef);
    }

    public long getHBaseServiceDefId() {
        return getId(hBaseServiceDef);
    }

    public long getHiveServiceDefId() {
        return getId(hiveServiceDef);
    }

    public long getKmsServiceDefId() {
        return getId(kmsServiceDef);
    }

    public long getKnoxServiceDefId() {
        return getId(knoxServiceDef);
    }

    public long getStormServiceDefId() {
        return getId(stormServiceDef);
    }

    public long getYarnServiceDefId() {
        return getId(yarnServiceDef);
    }

    public long getKafkaServiceDefId() {
        return getId(kafkaServiceDef);
    }

    public long getSolrServiceDefId() {
        return getId(solrServiceDef);
    }

    public long getNiFiServiceDefId() {
        return getId(nifiServiceDef);
    }

    public long getAtlasServiceDefId() {
        return getId(atlasServiceDef);
    }

    public long getSqoopServiceDefId() {
        return getId(sqoopServiceDef);
    }

    public long getTagServiceDefId() {
        return getId(tagServiceDef);
    }

    public long getWasbServiceDefId() {
        return getId(wasbServiceDef);
    }

    public long getKylinServiceDefId() {
        return getId(kylinServiceDef);
    }

    public ServiceDefInitializer(ServiceStore store, RangerConfiguration config) {
        this.store = store;
        this.config = config;
        createEmbeddedServiceDefs = config.getBoolean(PROPERTY_CREATE_EMBEDDED_SERVICE_DEFS, true);
        LOG.info("==> ServiceDefInitializer.init()");

        try {

            supportedServiceDefs = getSupportedServiceDef();
            /*
             * Maintaining the following service-def create-order is critical
             * for the the legacy service-defs (HDFS/HBase/Hive/Knox/Storm) to
             * be assigned IDs that were used in earlier version (0.4)
             */
            hdfsServiceDef = getOrCreateServiceDef(EMBEDDED_SERVICEDEF_HDFS_NAME);
            hBaseServiceDef = getOrCreateServiceDef(EMBEDDED_SERVICEDEF_HBASE_NAME);
            hiveServiceDef = getOrCreateServiceDef(EMBEDDED_SERVICEDEF_HIVE_NAME);
            kmsServiceDef = getOrCreateServiceDef(EMBEDDED_SERVICEDEF_KMS_NAME);
            knoxServiceDef = getOrCreateServiceDef(EMBEDDED_SERVICEDEF_KNOX_NAME);
            stormServiceDef = getOrCreateServiceDef(EMBEDDED_SERVICEDEF_STORM_NAME);
            yarnServiceDef = getOrCreateServiceDef(EMBEDDED_SERVICEDEF_YARN_NAME);
            kafkaServiceDef = getOrCreateServiceDef(EMBEDDED_SERVICEDEF_KAFKA_NAME);
            solrServiceDef = getOrCreateServiceDef(EMBEDDED_SERVICEDEF_SOLR_NAME);
            nifiServiceDef = getOrCreateServiceDef(EMBEDDED_SERVICEDEF_NIFI_NAME);
            atlasServiceDef = getOrCreateServiceDef(EMBEDDED_SERVICEDEF_ATLAS_NAME);

            tagServiceDef = getOrCreateServiceDef(EMBEDDED_SERVICEDEF_TAG_NAME);
            wasbServiceDef = getOrCreateServiceDef(EMBEDDED_SERVICEDEF_WASB_NAME);
            sqoopServiceDef = getOrCreateServiceDef(EMBEDDED_SERVICEDEF_SQOOP_NAME);
            kylinServiceDef = getOrCreateServiceDef(store, EMBEDDED_SERVICEDEF_KYLIN_NAME);

            // Ensure that tag service def is updated with access types of all
            // service defs
            store.updateTagServiceDefForAccessTypes();
        } catch (Throwable excp) {
            LOG.fatal("ServiceDefInitializer.init(): failed", excp);
        }

        LOG.info("<== ServiceDefInitializer.init()");
    }

    private long getId(RangerServiceDef serviceDef) {
        return serviceDef == null || serviceDef.getId() == null ? -1 : serviceDef.getId().longValue();
    }

    private RangerServiceDef getOrCreateServiceDef(String serviceDefName) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> ServiceDefInitializer.getOrCreateServiceDef(" + serviceDefName + ")");
        }

        RangerServiceDef ret = null;
        boolean createServiceDef = (CollectionUtils.isEmpty(supportedServiceDefs) || supportedServiceDefs.contains(serviceDefName));
        try {
            ret = store.getServiceDefByName(serviceDefName);
            if (ret == null && createEmbeddedServiceDefs && createServiceDef) {
                ret = ServiceDefUtil.normalize(EmbeddedServiceDefsUtil.instance().getEmbeddedServiceDef(serviceDefName));

                LOG.info("creating embedded service-def " + serviceDefName);
                if (ret.getId() != null) {
                    store.setPopulateExistingBaseFields(true);
                    try {
                        ret = store.createServiceDef(ret);
                    } finally {
                        store.setPopulateExistingBaseFields(false);
                    }
                } else {
                    ret = store.createServiceDef(ret);
                }
                LOG.info("created embedded service-def " + serviceDefName);
            }
        } catch (Exception excp) {
            LOG.fatal("ServiceDefInitializer.getOrCreateServiceDef(): failed to load/create serviceType " + serviceDefName, excp);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== ServiceDefInitializer.getOrCreateServiceDef(" + serviceDefName + "): " + ret);
        }

        return ret;
    }

    private Set<String> getSupportedServiceDef() {
        Set<String> supportedServiceDef = new HashSet<>();
        try {
            String ranger_supportedcomponents = config.get(PROPERTY_SUPPORTED_SERVICE_DEFS, DEFAULT_BOOTSTRAP_SERVICEDEF_LIST);
            if (StringUtils.isBlank(ranger_supportedcomponents) || "all".equalsIgnoreCase(ranger_supportedcomponents)) {
                ranger_supportedcomponents = DEFAULT_BOOTSTRAP_SERVICEDEF_LIST;
            }
            String[] supportedComponents = ranger_supportedcomponents.split(",");
            if (supportedComponents != null && supportedComponents.length > 0) {
                for (String element : supportedComponents) {
                    if (!StringUtils.isBlank(element)) {
                        element = element.toLowerCase();
                        supportedServiceDef.add(element);
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error("ServiceDefInitializer.getSupportedServiceDef(): failed", ex);
        }
        return supportedServiceDef;
    }

}
