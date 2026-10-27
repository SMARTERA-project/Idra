/*******************************************************************************
 * Idra - Open Data Federation Platform
 * Copyright (C) 2021 Engineering Ingegneria Informatica S.p.A.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 ******************************************************************************/

package it.eng.idra.authentication;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType;
import org.jose4j.jwk.HttpsJwks;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver;
import org.jose4j.keys.resolvers.VerificationKeyResolver;

/**
 * Verifies Keycloak access tokens: signature against the realm JWKS (asymmetric
 * algorithms only), mandatory expiration and subject, and issuer.
 *
 * <p>The issuer is checked against the configured list when one is given;
 * otherwise it must be the configured realm, whatever public hostname
 * Keycloak advertises (the signature already binds the token to the realm
 * keys fetched from the internal host).
 */
public class KeycloakTokenVerifier {

  /** Thrown when a token is not a valid access token for the configured realm. */
  public static class InvalidTokenException extends Exception {
    private static final long serialVersionUID = 1L;

    public InvalidTokenException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static final String OIDC_SUFFIX = "/protocol/openid-connect";

  private static final int CLOCK_SKEW_SECONDS = 30;

  private final JwtConsumer consumer;

  private final Set<String> issuers;

  private final String realmPath;

  /**
   * Instantiates a verifier.
   *
   * @param keyResolver the verification key resolver
   * @param issuers     accepted issuers; empty to accept any issuer of the realm
   * @param realm       the realm name
   */
  public KeycloakTokenVerifier(VerificationKeyResolver keyResolver, Set<String> issuers,
      String realm) {
    this.consumer = new JwtConsumerBuilder().setVerificationKeyResolver(keyResolver)
        .setJwsAlgorithmConstraints(new AlgorithmConstraints(ConstraintType.WHITELIST,
            AlgorithmIdentifiers.RSA_USING_SHA256, AlgorithmIdentifiers.RSA_USING_SHA384,
            AlgorithmIdentifiers.RSA_USING_SHA512, AlgorithmIdentifiers.RSA_PSS_USING_SHA256,
            AlgorithmIdentifiers.RSA_PSS_USING_SHA384, AlgorithmIdentifiers.RSA_PSS_USING_SHA512,
            AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256,
            AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384,
            AlgorithmIdentifiers.ECDSA_USING_P521_CURVE_AND_SHA512))
        .setRequireExpirationTime().setRequireSubject()
        .setAllowedClockSkewInSeconds(CLOCK_SKEW_SECONDS).setSkipDefaultAudienceValidation()
        .build();
    this.issuers = issuers == null ? Collections.emptySet() : issuers;
    if (this.issuers.isEmpty() && StringUtils.isBlank(realm)) {
      throw new IllegalArgumentException("Either accepted issuers or the realm are required");
    }
    this.realmPath = "/realms/" + realm;
  }

  /**
   * Builds a verifier from the IdM configuration.
   *
   * @param protocol    the IdM protocol
   * @param host        the IdM host, ending with the realm OpenID Connect path
   * @param issuersList comma separated accepted issuers, optional
   * @param jwksUri     explicit JWKS URI, optional
   * @return the verifier
   */
  public static KeycloakTokenVerifier fromConfiguration(String protocol, String host,
      String issuersList, String jwksUri) {
    String base = StringUtils.removeEnd(StringUtils.removeEnd(host, "/"), OIDC_SUFFIX);
    if (!base.contains("://")) {
      base = protocol + "://" + base;
    }
    String realm = StringUtils.substringAfterLast(base, "/realms/");
    String location = StringUtils.isNotBlank(jwksUri) ? jwksUri.trim()
        : base + OIDC_SUFFIX + "/certs";
    HttpsJwks jwks = new HttpsJwks(location);
    Set<String> issuers = StringUtils.isBlank(issuersList) ? Collections.emptySet()
        : Arrays.stream(issuersList.split(",")).map(String::trim).filter(StringUtils::isNotBlank)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    return new KeycloakTokenVerifier(new HttpsJwksVerificationKeyResolver(jwks), issuers, realm);
  }

  /**
   * Verifies a token and returns its claims.
   *
   * @param token the compact JWS
   * @return the verified claims
   * @throws InvalidTokenException if the token is not valid
   */
  public JwtClaims verify(String token) throws InvalidTokenException {
    try {
      JwtClaims claims = consumer.processToClaims(token);
      String issuer = claims.getIssuer();
      boolean accepted = issuers.isEmpty()
          ? issuer != null && StringUtils.removeEnd(issuer, "/").endsWith(realmPath)
          : issuers.contains(issuer);
      if (!accepted) {
        throw new InvalidTokenException("Issuer not accepted: " + issuer, null);
      }
      return claims;
    } catch (InvalidJwtException | MalformedClaimException e) {
      throw new InvalidTokenException("Token not valid: " + e.getMessage(), e);
    }
  }

  /**
   * Whether the value has the compact JWS shape (three dot separated parts).
   *
   * @param token the token
   * @return true for a compact JWS
   */
  public static boolean isCompactJws(String token) {
    return token != null && token.split("\\.", -1).length == 3;
  }
}
