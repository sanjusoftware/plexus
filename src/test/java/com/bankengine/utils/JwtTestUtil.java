package com.bankengine.utils;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.List;

public class JwtTestUtil {

    private final JwtEncoder jwtEncoder;
    private final String issuerUri;

    public JwtTestUtil(String secretKey, String issuerUri) {
        this.issuerUri = issuerUri;
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(secretKey.getBytes(), "HmacSHA256")));
    }

    public String createToken(String subject, List<String> permissions) {
        Instant now = Instant.now();
        long expiry = 36000L;

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuerUri)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expiry))
                .subject(subject)
                .claim("permissions", permissions)
                .build();

        JwtEncoderParameters encoderParameters = JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims);
        return this.jwtEncoder.encode(encoderParameters).getTokenValue();
    }
}