/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.crypto.key.kms.server;

import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.crypto.key.KeyProvider;
import org.apache.hadoop.crypto.key.KeyProvider.KeyVersion;
import org.apache.hadoop.crypto.key.KeyProviderCryptoExtension;
import org.apache.hadoop.crypto.key.KeyProviderCryptoExtension.EncryptedKeyVersion;
import org.apache.hadoop.crypto.key.kms.KMSRESTConstants;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authorize.AuthorizationException;
import org.apache.hadoop.crypto.key.kms.KMSClientProvider;
import org.apache.hadoop.crypto.key.kms.server.KMSACLsType.Type;
import org.apache.hadoop.security.token.delegation.web.HttpUserGroupInformation;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class providing the REST bindings, via Jersey, for the KMS.
 */
@Path(KMSRESTConstants.SERVICE_VERSION)
@InterfaceAudience.Private
public class KMS {

  public static enum KMSOp {
    CREATE_KEY, DELETE_KEY, ROLL_NEW_VERSION,
    GET_KEYS, GET_KEYS_METADATA,
    GET_KEY_VERSIONS, GET_METADATA, GET_KEY_VERSION, GET_CURRENT_KEY,
    GENERATE_EEK, DECRYPT_EEK
  }

  private static final String KEY_NAME_VALIDATION = "[a-z,A-Z,0-9](?!.*--)(?!.*__)(?!.*-_)(?!.*_-)[\\w\\-\\_]*";

  private KeyProviderCryptoExtension provider;
  private KMSAudit kmsAudit;

  public KMS() throws Exception {
    provider = KMSWebApp.getKeyProvider();
    kmsAudit= KMSWebApp.getKMSAudit();
  }

  private void assertAccess(Type aclType, UserGroupInformation ugi,
      KMSOp operation, String clientIp) throws AccessControlException {
    KMSWebApp.getACLs().assertAccess(aclType, ugi, operation, null, clientIp);
  }

  private void assertAccess(Type aclType, UserGroupInformation ugi,
      KMSOp operation, String key, String clientIp) throws AccessControlException {
    KMSWebApp.getACLs().assertAccess(aclType, ugi, operation, key, clientIp);
  }

  private static KeyProvider.KeyVersion removeKeyMaterial(
      KeyProvider.KeyVersion keyVersion) {
    return new KMSClientProvider.KMSKeyVersion(keyVersion.getName(),
        keyVersion.getVersionName(), null);
  }

  private static URI getKeyURI(String name) throws URISyntaxException {
    return new URI(KMSRESTConstants.SERVICE_VERSION + "/" +
        KMSRESTConstants.KEY_RESOURCE + "/" + name);
  }

  @POST
  @Path(KMSRESTConstants.KEYS_RESOURCE)
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @SuppressWarnings("unchecked")
  public Response createKey(Map jsonKey, @Context HttpServletRequest request) throws Exception {
    KMSWebApp.getAdminCallsMeter().mark();
    UserGroupInformation user = HttpUserGroupInformation.get();
    final String name = (String) jsonKey.get(KMSRESTConstants.NAME_FIELD);
    KMSClientProvider.checkNotEmpty(name, KMSRESTConstants.NAME_FIELD);
    validateKeyName(name);
    assertAccess(Type.CREATE, user, KMSOp.CREATE_KEY, name, request.getRemoteAddr());
    String cipher = (String) jsonKey.get(KMSRESTConstants.CIPHER_FIELD);
    final String material = (String) jsonKey.get(KMSRESTConstants.MATERIAL_FIELD);
    int length = (jsonKey.containsKey(KMSRESTConstants.LENGTH_FIELD))
                 ? (Integer) jsonKey.get(KMSRESTConstants.LENGTH_FIELD) : 0;
    String description = (String)
        jsonKey.get(KMSRESTConstants.DESCRIPTION_FIELD);
    Map<String, String> attributes = (Map<String, String>)
        jsonKey.get(KMSRESTConstants.ATTRIBUTES_FIELD);
    if (material != null) {
      assertAccess(Type.SET_KEY_MATERIAL, user,
          KMSOp.CREATE_KEY, name, request.getRemoteAddr());
    }
    final KeyProvider.Options options = new KeyProvider.Options(
        KMSWebApp.getConfiguration());
    if (cipher != null) {
      options.setCipher(cipher);
    }
    if (length != 0) {
      options.setBitLength(length);
    }
    options.setDescription(description);
    options.setAttributes(attributes);

    KeyProvider.KeyVersion keyVersion = user.doAs(
        new PrivilegedExceptionAction<KeyVersion>() {
          @Override
          public KeyVersion run() throws Exception {
            KeyProvider.KeyVersion keyVersion = (material != null)
              ? provider.createKey(name, Base64.decodeBase64(material), options)
              : provider.createKey(name, options);
            provider.flush();
            return keyVersion;
          }
        }
    );

    kmsAudit.ok(user, KMSOp.CREATE_KEY, name, "UserProvidedMaterial:" +
        (material != null) + " Description:" + description);

    if (!KMSWebApp.getACLs().hasAccess(Type.GET, user, request.getRemoteAddr())) {
      keyVersion = removeKeyMaterial(keyVersion);
    }
    Map json = KMSServerJSONUtils.toJSON(keyVersion);
    String requestURL = KMSMDCFilter.getURL();
    int idx = requestURL.lastIndexOf(KMSRESTConstants.KEYS_RESOURCE);
    requestURL = requestURL.substring(0, idx);
    String keyURL = requestURL + KMSRESTConstants.KEY_RESOURCE + "/" + name;
    return Response.created(getKeyURI(name)).type(MediaType.APPLICATION_JSON).
        header("Location", keyURL).entity(json).build();
  }

  private void validateKeyName(String name) {
	  Pattern pattern = Pattern.compile(KEY_NAME_VALIDATION);
	  Matcher matcher = pattern.matcher(name);
	  if(!matcher.matches()){
		  throw new IllegalArgumentException("Key Name : " + name +
		          ", should start with alpha/numeric letters and can have special characters - (hypen) or _ (underscore)");
	  }
  }

  @DELETE
  @Path(KMSRESTConstants.KEY_RESOURCE + "/{name:.*}")
  public Response deleteKey(@PathParam("name") final String name, @Context HttpServletRequest request)
      throws Exception {
    KMSWebApp.getAdminCallsMeter().mark();
    UserGroupInformation user = HttpUserGroupInformation.get();
    assertAccess(Type.DELETE, user, KMSOp.DELETE_KEY, name, request.getRemoteAddr());
    KMSClientProvider.checkNotEmpty(name, "name");

    user.doAs(new PrivilegedExceptionAction<Void>() {
      @Override
      public Void run() throws Exception {
        provider.deleteKey(name);
        provider.flush();
        return null;
      }
    });

    kmsAudit.ok(user, KMSOp.DELETE_KEY, name, "");

    return Response.ok().build();
  }

  @POST
  @Path(KMSRESTConstants.KEY_RESOURCE + "/{name:.*}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response rolloverKey(@PathParam("name") final String name,
      Map jsonMaterial, @Context HttpServletRequest request) throws Exception {
    KMSWebApp.getAdminCallsMeter().mark();
    UserGroupInformation user = HttpUserGroupInformation.get();
    assertAccess(Type.ROLLOVER, user, KMSOp.ROLL_NEW_VERSION, name, request.getRemoteAddr());
    KMSClientProvider.checkNotEmpty(name, "name");
    final String material = (String)
        jsonMaterial.get(KMSRESTConstants.MATERIAL_FIELD);
    if (material != null) {
      assertAccess(Type.SET_KEY_MATERIAL, user,
          KMSOp.ROLL_NEW_VERSION, name, request.getRemoteAddr());
    }

    KeyProvider.KeyVersion keyVersion = user.doAs(
        new PrivilegedExceptionAction<KeyVersion>() {
          @Override
          public KeyVersion run() throws Exception {
            KeyVersion keyVersion = (material != null)
              ? provider.rollNewVersion(name, Base64.decodeBase64(material))
              : provider.rollNewVersion(name);
            provider.flush();
            return keyVersion;
          }
        }
    );

    kmsAudit.ok(user, KMSOp.ROLL_NEW_VERSION, name, "UserProvidedMaterial:" +
        (material != null) + " NewVersion:" + keyVersion.getVersionName());

    if (!KMSWebApp.getACLs().hasAccess(Type.GET, user, request.getRemoteAddr())) {
      keyVersion = removeKeyMaterial(keyVersion);
    }
    Map json = KMSServerJSONUtils.toJSON(keyVersion);
    return Response.ok().type(MediaType.APPLICATION_JSON).entity(json).build();
  }

  @GET
  @Path(KMSRESTConstants.KEYS_METADATA_RESOURCE)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getKeysMetadata(@QueryParam(KMSRESTConstants.KEY)
      List<String> keyNamesList, @Context HttpServletRequest request) throws Exception {
    KMSWebApp.getAdminCallsMeter().mark();
    UserGroupInformation user = HttpUserGroupInformation.get();
    final String[] keyNames = keyNamesList.toArray(
        new String[keyNamesList.size()]);
    assertAccess(Type.GET_METADATA, user, KMSOp.GET_KEYS_METADATA, request.getRemoteAddr());

    KeyProvider.Metadata[] keysMeta = user.doAs(
        new PrivilegedExceptionAction<KeyProvider.Metadata[]>() {
          @Override
          public KeyProvider.Metadata[] run() throws Exception {
            return provider.getKeysMetadata(keyNames);
          }
        }
    );

    Object json = KMSServerJSONUtils.toJSON(keyNames, keysMeta);
    kmsAudit.ok(user, KMSOp.GET_KEYS_METADATA, "");
    return Response.ok().type(MediaType.APPLICATION_JSON).entity(json).build();
  }

  @GET
  @Path(KMSRESTConstants.KEYS_NAMES_RESOURCE)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getKeyNames(@Context HttpServletRequest request) throws Exception {
    KMSWebApp.getAdminCallsMeter().mark();
    UserGroupInformation user = HttpUserGroupInformation.get();

    List<String> unfiltered = user.doAs(
        new PrivilegedExceptionAction<List<String>>() {
          @Override
          public List<String> run() throws Exception {
            return provider.getKeys();
          }
        }
    );

    List<String> result = new ArrayList<>(unfiltered.size());
    for (String keyname : unfiltered) {
        try {
            assertAccess(Type.GET_KEYS, user, KMSOp.GET_KEYS, keyname, request.getRemoteAddr());
            result.add(keyname);
        } catch (AuthorizationException authorizationException) {
            // We are fine, we hide this keyname
        }
    }

    kmsAudit.ok(user, KMSOp.GET_KEYS, ""+unfiltered);
    return Response.ok().type(MediaType.APPLICATION_JSON).entity(result).build();
  }

  @GET
  @Path(KMSRESTConstants.KEY_RESOURCE + "/{name:.*}")
  public Response getKey(@PathParam("name") String name, @Context HttpServletRequest request)
      throws Exception {
    return getMetadata(name, request);
  }

  @GET
  @Path(KMSRESTConstants.KEY_RESOURCE + "/{name:.*}/" +
      KMSRESTConstants.METADATA_SUB_RESOURCE)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getMetadata(@PathParam("name") final String name, @Context HttpServletRequest request)
      throws Exception {
    UserGroupInformation user = HttpUserGroupInformation.get();
    KMSClientProvider.checkNotEmpty(name, "name");
    KMSWebApp.getAdminCallsMeter().mark();
    assertAccess(Type.GET_METADATA, user, KMSOp.GET_METADATA, name, request.getRemoteAddr());

    KeyProvider.Metadata metadata = user.doAs(
        new PrivilegedExceptionAction<KeyProvider.Metadata>() {
          @Override
          public KeyProvider.Metadata run() throws Exception {
            return provider.getMetadata(name);
          }
        }
    );

    Object json = KMSServerJSONUtils.toJSON(name, metadata);
    kmsAudit.ok(user, KMSOp.GET_METADATA, name, "");
    return Response.ok().type(MediaType.APPLICATION_JSON).entity(json).build();
  }

  @GET
  @Path(KMSRESTConstants.KEY_RESOURCE + "/{name:.*}/" +
      KMSRESTConstants.CURRENT_VERSION_SUB_RESOURCE)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getCurrentVersion(@PathParam("name") final String name, @Context HttpServletRequest request)
      throws Exception {
    UserGroupInformation user = HttpUserGroupInformation.get();
    KMSClientProvider.checkNotEmpty(name, "name");
    KMSWebApp.getKeyCallsMeter().mark();
    assertAccess(Type.GET, user, KMSOp.GET_CURRENT_KEY, name, request.getRemoteAddr());

    KeyVersion keyVersion = user.doAs(
        new PrivilegedExceptionAction<KeyVersion>() {
          @Override
          public KeyVersion run() throws Exception {
            return provider.getCurrentKey(name);
          }
        }
    );
    Object json = KMSServerJSONUtils.toJSON(keyVersion);
    if (keyVersion != null) {
    	kmsAudit.ok(user, KMSOp.GET_CURRENT_KEY, name, "");
    	return Response.ok().type(MediaType.APPLICATION_JSON).entity(json).build();
    }else{
    	return Response.status(404).type(MediaType.APPLICATION_JSON).entity(json).build();
    }
  }

  @GET
  @Path(KMSRESTConstants.KEY_VERSION_RESOURCE + "/{versionName:.*}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getKeyVersion(
      @PathParam("versionName") final String versionName, @Context HttpServletRequest request) throws Exception {
    UserGroupInformation user = HttpUserGroupInformation.get();
    KMSClientProvider.checkNotEmpty(versionName, "versionName");
    KMSWebApp.getKeyCallsMeter().mark();
    assertAccess(Type.GET, user, KMSOp.GET_KEY_VERSION, request.getRemoteAddr());

    KeyVersion keyVersion = user.doAs(
        new PrivilegedExceptionAction<KeyVersion>() {
          @Override
          public KeyVersion run() throws Exception {
            return provider.getKeyVersion(versionName);
          }
        }
    );

    if (keyVersion != null) {
      kmsAudit.ok(user, KMSOp.GET_KEY_VERSION, keyVersion.getName(), "");
    }
    Object json = KMSServerJSONUtils.toJSON(keyVersion);
    return Response.ok().type(MediaType.APPLICATION_JSON).entity(json).build();
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  @GET
  @Path(KMSRESTConstants.KEY_RESOURCE + "/{name:.*}/" +
      KMSRESTConstants.EEK_SUB_RESOURCE)
  @Produces(MediaType.APPLICATION_JSON)
  public Response generateEncryptedKeys(
          @PathParam("name") final String name,
          @QueryParam(KMSRESTConstants.EEK_OP) String edekOp,
          @DefaultValue("1")
          @QueryParam(KMSRESTConstants.EEK_NUM_KEYS) final int numKeys, @Context HttpServletRequest request)
          throws Exception {
    UserGroupInformation user = HttpUserGroupInformation.get();
    KMSClientProvider.checkNotEmpty(name, "name");
    KMSClientProvider.checkNotNull(edekOp, "eekOp");

    Object retJSON;
    if (edekOp.equals(KMSRESTConstants.EEK_GENERATE)) {
      assertAccess(Type.GENERATE_EEK, user, KMSOp.GENERATE_EEK, name, request.getRemoteAddr());

      final List<EncryptedKeyVersion> retEdeks =
          new LinkedList<EncryptedKeyVersion>();
      try {

        user.doAs(
            new PrivilegedExceptionAction<Void>() {
              @Override
              public Void run() throws Exception {
                for (int i = 0; i < numKeys; i++) {
                  retEdeks.add(provider.generateEncryptedKey(name));
                }
                return null;
              }
            }
        );

      } catch (Exception e) {
        throw new IOException(e);
      }
      kmsAudit.ok(user, KMSOp.GENERATE_EEK, name, "");
      retJSON = new ArrayList();
      for (EncryptedKeyVersion edek : retEdeks) {
        ((ArrayList)retJSON).add(KMSServerJSONUtils.toJSON(edek));
      }
    } else {
      throw new IllegalArgumentException("Wrong " + KMSRESTConstants.EEK_OP +
          " value, it must be " + KMSRESTConstants.EEK_GENERATE + " or " +
          KMSRESTConstants.EEK_DECRYPT);
    }
    KMSWebApp.getGenerateEEKCallsMeter().mark();
    return Response.ok().type(MediaType.APPLICATION_JSON).entity(retJSON)
        .build();
  }

  @SuppressWarnings("rawtypes")
  @POST
  @Path(KMSRESTConstants.KEY_VERSION_RESOURCE + "/{versionName:.*}/" +
      KMSRESTConstants.EEK_SUB_RESOURCE)
  @Produces(MediaType.APPLICATION_JSON)
  public Response decryptEncryptedKey(
      @PathParam("versionName") final String versionName,
      @QueryParam(KMSRESTConstants.EEK_OP) String eekOp,
      Map jsonPayload, @Context HttpServletRequest request)
      throws Exception {
    UserGroupInformation user = HttpUserGroupInformation.get();
    KMSClientProvider.checkNotEmpty(versionName, "versionName");
    KMSClientProvider.checkNotNull(eekOp, "eekOp");

    final String keyName = (String) jsonPayload.get(
        KMSRESTConstants.NAME_FIELD);
    String ivStr = (String) jsonPayload.get(KMSRESTConstants.IV_FIELD);
    String encMaterialStr =
        (String) jsonPayload.get(KMSRESTConstants.MATERIAL_FIELD);
    Object retJSON;
    if (eekOp.equals(KMSRESTConstants.EEK_DECRYPT)) {
      assertAccess(Type.DECRYPT_EEK, user, KMSOp.DECRYPT_EEK, keyName, request.getRemoteAddr());
      KMSClientProvider.checkNotNull(ivStr, KMSRESTConstants.IV_FIELD);
      final byte[] iv = Base64.decodeBase64(ivStr);
      KMSClientProvider.checkNotNull(encMaterialStr,
          KMSRESTConstants.MATERIAL_FIELD);
      final byte[] encMaterial = Base64.decodeBase64(encMaterialStr);

      KeyProvider.KeyVersion retKeyVersion = user.doAs(
          new PrivilegedExceptionAction<KeyVersion>() {
            @Override
            public KeyVersion run() throws Exception {
              return provider.decryptEncryptedKey(
                  new KMSClientProvider.KMSEncryptedKeyVersion(keyName,
                      versionName, iv, KeyProviderCryptoExtension.EEK,
                      encMaterial)
              );
            }
          }
      );

      retJSON = KMSServerJSONUtils.toJSON(retKeyVersion);
      kmsAudit.ok(user, KMSOp.DECRYPT_EEK, keyName, "");
    } else {
      throw new IllegalArgumentException("Wrong " + KMSRESTConstants.EEK_OP +
          " value, it must be " + KMSRESTConstants.EEK_GENERATE + " or " +
          KMSRESTConstants.EEK_DECRYPT);
    }
    KMSWebApp.getDecryptEEKCallsMeter().mark();
    return Response.ok().type(MediaType.APPLICATION_JSON).entity(retJSON)
        .build();
  }

  @GET
  @Path(KMSRESTConstants.KEY_RESOURCE + "/{name:.*}/" +
      KMSRESTConstants.VERSIONS_SUB_RESOURCE)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getKeyVersions(@PathParam("name") final String name, @Context HttpServletRequest request)
      throws Exception {
    UserGroupInformation user = HttpUserGroupInformation.get();
    KMSClientProvider.checkNotEmpty(name, "name");
    KMSWebApp.getKeyCallsMeter().mark();
    assertAccess(Type.GET, user, KMSOp.GET_KEY_VERSIONS, name, request.getRemoteAddr());

    List<KeyVersion> ret = user.doAs(
        new PrivilegedExceptionAction<List<KeyVersion>>() {
          @Override
          public List<KeyVersion> run() throws Exception {
            return provider.getKeyVersions(name);
          }
        }
    );

    Object json = KMSServerJSONUtils.toJSON(ret);
    kmsAudit.ok(user, KMSOp.GET_KEY_VERSIONS, name, "");
    return Response.ok().type(MediaType.APPLICATION_JSON).entity(json).build();
  }

}
