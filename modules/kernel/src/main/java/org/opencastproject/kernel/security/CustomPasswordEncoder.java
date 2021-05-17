/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.opencastproject.kernel.security;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password encoder using bcrypt for password hashing while still supporting the verification of olf md5 based
 * passwords.
 */
public class CustomPasswordEncoder implements PasswordEncoder {
  private Logger logger = LoggerFactory.getLogger(CustomPasswordEncoder.class);

  /**
   * Encode the raw password for storage using bcrypt.
   * @param rawPassword raw password to encrypt/hash
   * @return hashed password
   */
  @Override
  public String encode(final java.lang.CharSequence rawPassword) {
    return BCrypt.hashpw(rawPassword.toString(), BCrypt.gensalt());
  }

  /**
   * Verify the encoded password obtained from storage matches the submitted raw
   * password after it too is encoded. Returns true if the passwords match, false if
   * they do not. The stored password itself is never decoded.
   *
   * @param rawPassword the raw password to encode and match
   * @param encodedPassword the encoded password from storage to compare with
   * @return true if the raw password, after encoding, matches the encoded password from storage
   */
  @Override
  public boolean matches(java.lang.CharSequence rawPassword, String encodedPassword) {
    // Test BCrypt encoded hash
    logger.debug("Verifying bcrypt hash {}", encodedPassword);
    try {
      return StringUtils.startsWith(encodedPassword, "$") && BCrypt.checkpw(rawPassword.toString(), encodedPassword);
    } catch (IllegalArgumentException e) {
      logger.debug("bcrypt hash verification failed", e);
    }
    return false;
  }

  public boolean isPasswordValid(String encodedPassword, String rawPassword, Object salt) {
    // Test MD5 encoded hash
    if (encodedPassword.length() == 32) {
      final String hash = md5Encode(rawPassword, salt);
      logger.debug("Checking md5 hashed password '{}' against encoded password '{}'", hash, encodedPassword);
      return hash.equals(encodedPassword);
    }

    // Test BCrypt encoded hash
    matches(rawPassword, encodedPassword);
    return false;
  }

  /**
   * Encode a clear text password using Opencast's legacy MD5 based hashing with salt.
   * The username was used as salt for this.
   *
   * @param clearText
   *          the password
   * @param salt
   *          the salt
   * @return the hashed password
   * @throws IllegalArgumentException
   *           if clearText or salt are null
   */
  public static String md5Encode(String clearText, Object salt) throws IllegalArgumentException {
    if (clearText == null || salt == null)
      throw new IllegalArgumentException("clearText and salt must not be null");
    return DigestUtils.md5Hex(clearText + "{" + salt.toString() + "}");
  }

  @Override
  public boolean upgradeEncoding(String encodedPassword) {
    return false;
  }
}
