package realworld_backend.auth.infrastructure.stub;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.auth.domain.enumerous.SessionStatus;
import realworld_backend.auth.domain.exception.UserAuthException;
import realworld_backend.auth.domain.model.AuthRefreshToken;
import realworld_backend.auth.domain.service.AuthRefreshTokenService;
import realworld_backend.auth.repository.AuthRefreshTokenRepository;
import realworld_backend.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Component
@AllArgsConstructor
public class AuthRefreshTokenServiceImpl implements AuthRefreshTokenService {

    private final AuthRefreshTokenRepository authRefreshTokenRepository;

    @Override
    @Transactional
    public String generateRefreshToken(Long userId, String deviceId, String sessionId) {
        LocalDateTime now = UtcTimeMapper.nowUtc();
        //find all the family refreshToken
        List<String> activeFamilyIdsByDevice =
                authRefreshTokenRepository.findActiveFamilyIdsByDevice(userId, deviceId, SessionStatus.ACTIVE);
        if (!activeFamilyIdsByDevice.isEmpty()) {
            //revoke all of them
            authRefreshTokenRepository.revokeByFamilyIds(activeFamilyIdsByDevice, now);
        }
        String uuidString = UUID.randomUUID().toString();
        String refreshTokenValue = generate();
        String tokenHash = sha256(refreshTokenValue);
        AuthRefreshToken newRefreshToken = AuthRefreshToken.builder().expiresAt(now.plusDays(7))
                .familyId(uuidString)
                .revokedAt(null)
                .createdAt(now)
                .sessionId(sessionId)
                .usedAt(null)
                .tokenHash(tokenHash)
                .parentId(null).build();
        authRefreshTokenRepository.save(newRefreshToken);
        return refreshTokenValue;
    }

    @Override
    public void revokeBySessionId(String sessionId) {
        authRefreshTokenRepository.revokeBySessionId(sessionId, UtcTimeMapper.nowUtc());
    }

    @Override
    public AuthRefreshToken findByRefreshToken(String refreshToken) {
        //find By TokenHash And After ExpiresAt
        String tokenHash = sha256(refreshToken);
        Optional<AuthRefreshToken> authRefreshTokenOptional =
                authRefreshTokenRepository.findByTokenHashAndExpiresAtAfter(tokenHash, UtcTimeMapper.nowUtc());


        if (authRefreshTokenOptional.isPresent()) {
            AuthRefreshToken authRefreshToken = authRefreshTokenOptional.get();

            if (authRefreshToken.getUsedAt() != null || authRefreshToken.getRevokedAt() != null) {
                //the refreshToken is reused,need revoked the whole family
                authRefreshTokenRepository.revokeByFamilyIds(Collections.singletonList(authRefreshToken.getFamilyId()), UtcTimeMapper.nowUtc());
                throw new UserAuthException(ErrorCode.REFRESH_TOKEN_REUSED);
            }
            return authRefreshToken;
        }
        throw new UserAuthException(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Transactional
    @Override
    public String rotationRefreshToken(AuthRefreshToken authRefreshToken) {

        LocalDateTime now = UtcTimeMapper.nowUtc();
        //prevent concurrent operation
        int updated = authRefreshTokenRepository.consumeIfActive(authRefreshToken.getId(), now);
        if (updated == 0) {
            throw new UserAuthException(ErrorCode.REFRESH_TOKEN_REUSED); //
        }

        //generate new refreshToken
        String refreshToken = generate();
        String tokenHash = sha256(refreshToken);
        AuthRefreshToken newRefreshToken = AuthRefreshToken.builder()
                .parentId(authRefreshToken.getId())
                .familyId(authRefreshToken.getFamilyId())
                .sessionId(authRefreshToken.getSessionId())
                .createdAt(now)
                .expiresAt(now.plusDays(7))
                .usedAt(null)
                .tokenHash(tokenHash)
                .revokedAt(null)
                .build();

        authRefreshTokenRepository.save(newRefreshToken);
        return refreshToken;
    }


    public String generate() {
        final int TOKEN_BYTES = 64;
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    public static String sha256(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash token", e);
        }
    }

}

