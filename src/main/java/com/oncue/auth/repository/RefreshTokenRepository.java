package com.oncue.auth.repository;

import com.oncue.auth.model.RefreshTokenEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshTokenEntity token where token.tokenHash = :tokenHash and token.revokedAt is null")
    Optional<RefreshTokenEntity> findByTokenHashAndRevokedAtIsNull(@Param("tokenHash") String tokenHash);

    void deleteAllByUserId(Long userId);
}
