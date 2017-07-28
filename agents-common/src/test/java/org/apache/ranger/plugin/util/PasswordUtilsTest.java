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

package org.apache.ranger.plugin.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.security.NoSuchAlgorithmException;
import org.apache.ranger.plugin.service.RangerServiceException;

import org.junit.Test;

import com.google.common.base.Joiner;

public class PasswordUtilsTest {

	@Test
	public void testEncrypt() throws RangerServiceException {
		// encryption of password that contains no configuration info is using legacy
		// cryptography algorithm for backward compatibility.
		String encryptedPassword = PasswordUtils.encryptPassword("secretPasswordNoOneWillEverKnow");
		assertNotNull(encryptedPassword);
		assertEquals("ljoJ3gf4T018Xr+BujPAqBDW8Onp1PqprsLKmxus8pGGBETtAVU6OQ==", encryptedPassword);
	}

	@Test
	public void testDecrypt() throws RangerServiceException {
		String decryptedPassword = PasswordUtils
				.decryptPassword("ljoJ3gf4T018Xr+BujPAqBDW8Onp1PqprsLKmxus8pGGBETtAVU6OQ==");

		assertNotNull(decryptedPassword);
		assertEquals("secretPasswordNoOneWillEverKnow", decryptedPassword);
	}

	@Test
	public void testEncryptWithExplicitDefaultWeakAlgorithm() throws RangerServiceException {
		String freeTextPasswordMetaData = join("PBEWithMD5AndDES", "ENCRYPT_KEY", "SALTSALT", "4");
		String encryptedPassword = PasswordUtils
				.encryptPassword(join(freeTextPasswordMetaData, "secretPasswordNoOneWillEverKnow"));
		assertNotNull(encryptedPassword);

		String decryptedPassword = PasswordUtils.decryptPassword(join(freeTextPasswordMetaData, encryptedPassword));
		assertEquals("secretPasswordNoOneWillEverKnow", decryptedPassword);
	}

	@Test
	public void testEncryptWithSHA1AndDESede() throws RangerServiceException {
		String freeTextPasswordMetaData = join("PBEWithSHA1AndDESede", "ENCRYPT_KEY", "SALTSALT", "4");
		String encryptedPassword = PasswordUtils
				.encryptPassword(join(freeTextPasswordMetaData, "secretPasswordNoOneWillEverKnow"));
		assertNotNull(encryptedPassword);

		String decryptedPassword = PasswordUtils.decryptPassword(join(freeTextPasswordMetaData, encryptedPassword));
		assertEquals("secretPasswordNoOneWillEverKnow", decryptedPassword);
	}

	@Test
	public void testEncryptWithSHA512AndAES128() throws RangerServiceException, NoSuchAlgorithmException {
		String freeTextPasswordMetaData = join("PBEWITHHMACSHA512ANDAES_128", "ENCRYPT_KEY", "SALTSALT", "4",
				PasswordUtils.generateIvIfNeeded("PBEWITHHMACSHA512ANDAES_128"));
		String encryptedPassword = PasswordUtils
				.encryptPassword(join(freeTextPasswordMetaData, "secretPasswordNoOneWillEverKnow"));

		assertNotNull(encryptedPassword);
		String decryptedPassword = PasswordUtils.decryptPassword(join(freeTextPasswordMetaData, encryptedPassword));
		assertEquals("secretPasswordNoOneWillEverKnow", decryptedPassword);
	}

	@Test
	public void testEncryptWithSHA512AndAES128WithMultipleComasInPass() throws RangerServiceException {
		String freeTextPasswordMetaData = "PBEWITHHMACSHA512ANDAES_128,tzL1AKl5uc4NKYaoQ4P3WLGIBFPXWPWdu1fRm9004jtQiV,f77aLYLo,1000,9f3vNL0ijeHF4RWN/yUo0A==";
		String freeTextPassword = "asd,qwe,123";
		String encryptedPassword = PasswordUtils.encryptPassword(join(freeTextPasswordMetaData, freeTextPassword));

		assertNotNull(encryptedPassword);
		String decryptedPassword = PasswordUtils.decryptPassword(join(freeTextPasswordMetaData, encryptedPassword));
		assertEquals(freeTextPassword, decryptedPassword);
	}

	@Test
	public void testEncryptWithSHA512AndAES128WithSingleComaInPass() throws RangerServiceException {
		String freeTextPasswordMetaData = "PBEWITHHMACSHA512ANDAES_128,tzL1AKl5uc4NKYaoQ4P3WLGIBFPXWPWdu1fRm9004jtQiV,f77aLYLo,1000,9f3vNL0ijeHF4RWN/yUo0A==";
		String freeTextPassword = "asd,123";
		String encryptedPassword = PasswordUtils.encryptPassword(join(freeTextPasswordMetaData, freeTextPassword));

		assertNotNull(encryptedPassword);
		String decryptedPassword = PasswordUtils.decryptPassword(join(freeTextPasswordMetaData, encryptedPassword));
		assertEquals(freeTextPassword, decryptedPassword);
	}

	@Test
	public void testEncryptWithSHA512AndAES128EndingWithSingleComa() throws RangerServiceException {
		String freeTextPasswordMetaData = "PBEWITHHMACSHA512ANDAES_128,tzL1AKl5uc4NKYaoQ4P3WLGIBFPXWPWdu1fRm9004jtQiV,f77aLYLo,1000,9f3vNL0ijeHF4RWN/yUo0A==";
		String freeTextPassword = "asd,";
		String encryptedPassword = PasswordUtils.encryptPassword(join(freeTextPasswordMetaData, freeTextPassword));

		assertNotNull(encryptedPassword);
		String decryptedPassword = PasswordUtils.decryptPassword(join(freeTextPasswordMetaData, encryptedPassword));
		assertEquals(freeTextPassword, decryptedPassword);
	}

	@Test
	public void testEncryptWithSHA512AndAES128StartingWithSingleComa() throws RangerServiceException {
		String freeTextPasswordMetaData = "PBEWITHHMACSHA512ANDAES_128,tzL1AKl5uc4NKYaoQ4P3WLGIBFPXWPWdu1fRm9004jtQiV,f77aLYLo,1000,9f3vNL0ijeHF4RWN/yUo0A==";
		String freeTextPassword = ",asd";
		String encryptedPassword = PasswordUtils.encryptPassword(join(freeTextPasswordMetaData, freeTextPassword));

		assertNotNull(encryptedPassword);
		String decryptedPassword = PasswordUtils.decryptPassword(join(freeTextPasswordMetaData, encryptedPassword));
		assertEquals(freeTextPassword, decryptedPassword);
	}

	@Test
	public void testEncryptWithSHA512AndAES128MultipleComasInTheEnd() throws RangerServiceException {
		String freeTextPasswordMetaData = "PBEWITHHMACSHA512ANDAES_128,tzL1AKl5uc4NKYaoQ4P3WLGIBFPXWPWdu1fRm9004jtQiV,f77aLYLo,1000,9f3vNL0ijeHF4RWN/yUo0A==";
		String freeTextPassword = "asd,,";
		String encryptedPassword = PasswordUtils.encryptPassword(join(freeTextPasswordMetaData, freeTextPassword));

		assertNotNull(encryptedPassword);
		String decryptedPassword = PasswordUtils.decryptPassword(join(freeTextPasswordMetaData, encryptedPassword));
		assertEquals(freeTextPassword, decryptedPassword);
	}

	@Test
	public void testEncryptWithSHA512AndAES128MultipleComasSurroundingText()
			throws RangerServiceException {
		String freeTextPasswordMetaData = "PBEWITHHMACSHA512ANDAES_128,tzL1AKl5uc4NKYaoQ4P3WLGIBFPXWPWdu1fRm9004jtQiV,f77aLYLo,1000,9f3vNL0ijeHF4RWN/yUo0A==";
		String freeTextPassword = ",,a,,";
		String encryptedPassword = PasswordUtils.encryptPassword(join(freeTextPasswordMetaData, freeTextPassword));

		assertNotNull(encryptedPassword);
		String decryptedPassword = PasswordUtils.decryptPassword(join(freeTextPasswordMetaData, encryptedPassword));
		assertEquals(freeTextPassword, decryptedPassword);
	}

	@Test
	public void testEncryptWithSHA512AndAES128MultipleComasBeforeText() throws RangerServiceException {
		String freeTextPasswordMetaData = "PBEWITHHMACSHA512ANDAES_128,tzL1AKl5uc4NKYaoQ4P3WLGIBFPXWPWdu1fRm9004jtQiV,f77aLYLo,1000,9f3vNL0ijeHF4RWN/yUo0A==";
		String freeTextPassword = ",,,a";
		String encryptedPassword = PasswordUtils.encryptPassword(join(freeTextPasswordMetaData, freeTextPassword));

		assertNotNull(encryptedPassword);
		String decryptedPassword = PasswordUtils.decryptPassword(join(freeTextPasswordMetaData, encryptedPassword));
		assertEquals(freeTextPassword, decryptedPassword);
	}

	@Test
	public void testEncryptWithSHA512AndAES128MultipleComasOnlyPassword() throws RangerServiceException {
		String freeTextPasswordMetaData = "PBEWITHHMACSHA512ANDAES_128,tzL1AKl5uc4NKYaoQ4P3WLGIBFPXWPWdu1fRm9004jtQiV,f77aLYLo,1000,9f3vNL0ijeHF4RWN/yUo0A==";
		String freeTextPassword = ",,,";
		String encryptedPassword = PasswordUtils.encryptPassword(join(freeTextPasswordMetaData, freeTextPassword));

		assertNotNull(encryptedPassword);
		String decryptedPassword = PasswordUtils.decryptPassword(join(freeTextPasswordMetaData, encryptedPassword));
		assertEquals(freeTextPassword, decryptedPassword);
	}

	@Test
	public void testEncryptWithSHA512AndAES128SingleComaOnlyPassword() throws RangerServiceException {
		String freeTextPasswordMetaData = "PBEWITHHMACSHA512ANDAES_128,tzL1AKl5uc4NKYaoQ4P3WLGIBFPXWPWdu1fRm9004jtQiV,f77aLYLo,1000,9f3vNL0ijeHF4RWN/yUo0A==";
		String freeTextPassword = ",";
		String encryptedPassword = PasswordUtils.encryptPassword(join(freeTextPasswordMetaData, freeTextPassword));

		assertNotNull(encryptedPassword);
		String decryptedPassword = PasswordUtils.decryptPassword(join(freeTextPasswordMetaData, encryptedPassword));
		assertEquals(freeTextPassword, decryptedPassword);
	}

	@Test
	public void testDecryptEmptyResultInNull() throws Throwable {
		String string0 = PasswordUtils.decryptPassword("");
		assertNull(string0);
	}

	private String join(String... strings) {
		return Joiner.on(",").skipNulls().join(strings);
	}
}
