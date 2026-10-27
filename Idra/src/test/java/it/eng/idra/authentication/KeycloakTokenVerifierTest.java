package it.eng.idra.authentication;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.keys.HmacKey;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeycloakTokenVerifierTest {
  private static final String ISSUER = "http://localhost:18180/auth/realms/smartera";
  private final RsaJsonWebKey realmKey = key("realm");
  private final RsaJsonWebKey otherKey = key("other");

  private static RsaJsonWebKey key(String kid) {
    try {
      RsaJsonWebKey k = RsaJwkGenerator.generateJwk(2048);k.setKeyId(kid);return k;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private KeycloakTokenVerifier verifier(Set<String> issuers) {
    List<JsonWebKey> keys = Collections.singletonList(realmKey);
    return new KeycloakTokenVerifier(new JwksVerificationKeyResolver(keys), issuers, "smartera");
  }

  private JwtClaims claims(String issuer) {
    JwtClaims c = new JwtClaims();c.setIssuer(issuer);c.setSubject("user-1");c.setExpirationTimeMinutesInTheFuture(5);
    c.setIssuedAtToNow();c.setClaim("realm_access", Collections.singletonMap("roles", Collections.singletonList("IDRA_ADMIN")));
    return c;
  }

  private String sign(JwtClaims c, RsaJsonWebKey k) throws Exception {
    JsonWebSignature jws = new JsonWebSignature();jws.setPayload(c.toJson());jws.setKey(k.getPrivateKey());
    jws.setKeyIdHeaderValue(k.getKeyId());jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    return jws.getCompactSerialization();
  }

  @Test void acceptsRealmSignedTokenWithPublicIssuer() throws Exception {
    JwtClaims verified = verifier(Collections.emptySet()).verify(sign(claims(ISSUER), realmKey));
    assertEquals("user-1", verified.getSubject());
  }

  @Test void rejectsPayloadForgedUnderAValidSignature() throws Exception {
    String[] parts = sign(claims(ISSUER), realmKey).split("\\.");
    JwtClaims forged = claims(ISSUER);forged.setSubject("attacker");forged.setExpirationTimeMinutesInTheFuture(600);
    String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(forged.toJson().getBytes(StandardCharsets.UTF_8));
    assertThrows(KeycloakTokenVerifier.InvalidTokenException.class, () -> verifier(Collections.emptySet()).verify(parts[0] + "." + payload + "." + parts[2]));
  }

  @Test void rejectsKeysOutsideTheRealmJwks() throws Exception {
    assertThrows(KeycloakTokenVerifier.InvalidTokenException.class, () -> verifier(Collections.emptySet()).verify(sign(claims(ISSUER), otherKey)));
  }

  @Test void rejectsUnsignedAndSymmetricTokens() throws Exception {
    JsonWebSignature none = new JsonWebSignature();none.setPayload(claims(ISSUER).toJson());
    none.setAlgorithmConstraints(org.jose4j.jwa.AlgorithmConstraints.NO_CONSTRAINTS);none.setAlgorithmHeaderValue(AlgorithmIdentifiers.NONE);
    String unsigned = none.getCompactSerialization();
    assertThrows(KeycloakTokenVerifier.InvalidTokenException.class, () -> verifier(Collections.emptySet()).verify(unsigned));
    JsonWebSignature hmac = new JsonWebSignature();hmac.setPayload(claims(ISSUER).toJson());hmac.setKeyIdHeaderValue("realm");
    hmac.setAlgorithmHeaderValue(AlgorithmIdentifiers.HMAC_SHA256);hmac.setKey(new HmacKey(new byte[32]));hmac.setDoKeyValidation(false);
    String symmetric = hmac.getCompactSerialization();
    assertThrows(KeycloakTokenVerifier.InvalidTokenException.class, () -> verifier(Collections.emptySet()).verify(symmetric));
  }

  @Test void requiresUnexpiredTokensWithExpirationAndSubject() throws Exception {
    JwtClaims expired = claims(ISSUER);expired.setExpirationTimeMinutesInTheFuture(-5);
    assertThrows(KeycloakTokenVerifier.InvalidTokenException.class, () -> verifier(Collections.emptySet()).verify(sign(expired, realmKey)));
    JwtClaims noExp = claims(ISSUER);noExp.unsetClaim("exp");
    assertThrows(KeycloakTokenVerifier.InvalidTokenException.class, () -> verifier(Collections.emptySet()).verify(sign(noExp, realmKey)));
    JwtClaims noSub = claims(ISSUER);noSub.unsetClaim("sub");
    assertThrows(KeycloakTokenVerifier.InvalidTokenException.class, () -> verifier(Collections.emptySet()).verify(sign(noSub, realmKey)));
  }

  @Test void checksIssuerAgainstRealmOrConfiguredList() throws Exception {
    String otherRealm = sign(claims("http://localhost:18180/auth/realms/master"), realmKey);
    assertThrows(KeycloakTokenVerifier.InvalidTokenException.class, () -> verifier(Collections.emptySet()).verify(otherRealm));
    JwtClaims withoutIssuer = claims(ISSUER);withoutIssuer.unsetClaim("iss");String noIssuer = sign(withoutIssuer, realmKey);
    assertThrows(KeycloakTokenVerifier.InvalidTokenException.class, () -> verifier(Collections.emptySet()).verify(noIssuer));
    KeycloakTokenVerifier pinned = verifier(Set.of("https://idra.example.org/auth/realms/smartera"));
    assertThrows(KeycloakTokenVerifier.InvalidTokenException.class, () -> pinned.verify(sign(claims(ISSUER), realmKey)));
    assertEquals("user-1", pinned.verify(sign(claims("https://idra.example.org/auth/realms/smartera"), realmKey)).getSubject());
  }

  @Test void recognisesCompactJws() {
    assertTrue(KeycloakTokenVerifier.isCompactJws("a.b.c"));
    assertFalse(KeycloakTokenVerifier.isCompactJws("not-a-jwt"));
    assertFalse(KeycloakTokenVerifier.isCompactJws(null));
  }
}
