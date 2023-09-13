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

package org.opencastproject.security.jwt;

import org.opencastproject.security.api.JaxbOrganization;
import org.opencastproject.security.api.JaxbRole;
import org.opencastproject.security.api.JaxbUser;
import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.User;
import org.opencastproject.security.impl.jpa.JpaOrganization;
import org.opencastproject.security.impl.jpa.JpaRole;
import org.opencastproject.userdirectory.api.UserReferenceProvider;

import com.auth0.jwk.JwkException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// TODO: Proper class and variable naming
// TODO: Proper parameter verification
public class TestFilter extends GenericFilterBean {

  private static final Logger logger = LoggerFactory.getLogger(TestFilter.class);
  private SecurityService securityService;
  private UserReferenceProvider userReferenceProvider;

  private String parameterName = "jwt";
  /** JWKS URL to use for JWT validation (asymmetric algorithms). */
  private String jwksUrl = null;
  /** Mapping used to extract roles from the JWT. */
  private GuavaCachedUrlJwkProvider jwkProvider;
  /** Number of minutes fetched JWKs will be cached. */
  private int jwksCacheExpiresIn = 60 * 24;
  /** Secret to use for JWT validation (symmetric algorithms). */
  private String secret = null;
  /** Allowed algorithms with which a valid JWT may be signed ('alg' claim). */
  private List<String> expectedAlgorithms = null;
  /** Mapping used to extract the username from the JWT. */
  private String rolesMapping = null;

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
          throws IOException, ServletException {
    if (jwksUrl != null) {
      jwkProvider = new GuavaCachedUrlJwkProvider(jwksUrl, jwksCacheExpiresIn, TimeUnit.MINUTES);
    }

    try {
      String token = servletRequest.getParameter(parameterName);
      if (token != null) {

        // Check if we have the "rolesOnly" header attribute
        // Constraints argument is not optional, even if we have none
        List<String> fakeConstraints = new ArrayList<>();
        DecodedJWT tmpJwt = decodeAndValidate(token, fakeConstraints);
        Claim rolesOnlyClaim = tmpJwt.getHeaderClaim("rolesOnly");
        String rolesOnly = rolesOnlyClaim.asString();

        // Only proceed if header attribute is true
        if (StringUtils.equals(rolesOnly, "true")) {
          // Grab roles from "roles" claim
          JpaOrganization jpaOrganization = fromOrganization(securityService.getOrganization());
          Set<JpaRole> roles = new HashSet<>();
          Set<String> rolesAsString = new HashSet<>();
          String rolesString = evaluateMapping(tmpJwt, rolesMapping, false);
          for (String role : rolesString.split(",")) {
            if (StringUtils.isNotBlank(role)) {
              roles.add(new JpaRole(role, jpaOrganization));
              rolesAsString.add(role);
            }
          }

          // Add roles to user session
          // TODO: This does not work
          Organization organization = securityService.getOrganization();
          User user = securityService.getUser();
          user = new JaxbUser(user.getUsername(), user.getPassword(), user.getName(), user.getEmail(), user.getProvider(),
              JaxbOrganization.fromOrganization(user.getOrganization()),
              rolesAsString.stream().map(role -> new JaxbRole(role, JaxbOrganization.fromOrganization(organization))).collect(Collectors.toSet()));
          logger.info("Request roles '{}' are amended to user '{}'", roles, user.getUsername());
          securityService.setUser(user);

        }

      }
    } catch (JWTVerificationException | JwkException exception) {
      logger.debug(exception.getMessage());
    }

    filterChain.doFilter(servletRequest, servletResponse);
  }

  // TODO: Most of the following functions are copied from DynamicLoginHandler.java
  // Move them into Util.java or another appropriate place to avoid code duplication

  /**
   * Decodes and validates a JWT.
   *
   * @param token The JWT string.
   * @return The decoded JWT.
   * @throws JwkException If the JWT fails to be validated.
   */
  private DecodedJWT decodeAndValidate(String token, List<String> claimConstraints) throws JwkException {
    DecodedJWT jwt;

    if (jwksUrl != null) {
      jwt = JWTVerifier.verify(token, jwkProvider, claimConstraints);
    } else {
      jwt = JWTVerifier.verify(token, secret, claimConstraints);
    }

    if (!expectedAlgorithms.contains(jwt.getAlgorithm())) {
      throw new JWTVerificationException(
          "JWT token was signed with an unexpected algorithm '" + jwt.getAlgorithm() + "'"
      );
    }

    return jwt;
  }

  /**
   * Converts a {@link Organization} object into a {@link JpaOrganization} object.
   *
   * @param org The {@link Organization} object.
   * @return The corresponding {@link JpaOrganization} object.
   */
  private JpaOrganization fromOrganization(Organization org) {
    if (org instanceof JpaOrganization) {
      return (JpaOrganization) org;
    }

    return new JpaOrganization(org.getId(), org.getName(), org.getServers(), org.getAdminRole(), org.getAnonymousRole(),
        org.getProperties());
  }

  /**
   * Evaluates a mapping given in SpEL on a decoded JWT.
   *
   * @param jwt The decoded JWT.
   * @param mapping The mapping.
   * @param ensureEncoding Whether to ensure UTF_8 encoding.
   *
   * @return The string evaluated from the mapping.
   */
  private String evaluateMapping(DecodedJWT jwt, String mapping, boolean ensureEncoding) {
    ExpressionParser parser = new SpelExpressionParser();
    Expression exp = parser.parseExpression(mapping);
    Map lol = jwt.getClaims();
    String value = exp.getValue(jwt.getClaims(), String.class);
    if (ensureEncoding) {
      value = new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }
    return value;
  }

  /**
   * Setter for the expected algorithms.
   *
   * @param expectedAlgorithms The expected algorithms.
   */
  public void setExpectedAlgorithms(List<String> expectedAlgorithms) {
    this.expectedAlgorithms = expectedAlgorithms;
  }

  /**
   * Setter for the roles mapping.
   * @param rolesMapping The roles mapping.
   */
  public void setRolesMapping(String rolesMapping) {
    this.rolesMapping = rolesMapping;
  }

  /**
   * Setter for the JWKS URL.
   *
   * @param jwksUrl The JWKS URL.
   */
  public void setJwksUrl(String jwksUrl) {
    this.jwksUrl = jwksUrl;
  }

  /**
   * Setter for the JWKS cache expiration.
   *
   * @param jwksCacheExpiresIn The number of minutes after which a cached JWKS expires.
   */
  public void setJwksCacheExpiresIn(int jwksCacheExpiresIn) {
    this.jwksCacheExpiresIn = jwksCacheExpiresIn;
  }

  /**
   * Setter for the secret used for JWT validation.
   *
   * @param secret The secret.
   */
  public void setSecret(String secret) {
    this.secret = secret;
  }

  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  public void setUserReferenceProvider(UserReferenceProvider userReferenceProvider) {
    this.userReferenceProvider = userReferenceProvider;
  }
}
