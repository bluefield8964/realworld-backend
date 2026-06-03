package realworld_backend.commerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import realworld_backend.commerce.model.entitlement.EntitlementGrant;
import realworld_backend.commerce.model.entitlement.enums.EntitlementResourceType;
import realworld_backend.commerce.model.entitlement.enums.EntitlementSourceType;
import realworld_backend.commerce.model.entitlement.enums.EntitlementStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EntitlementGrantRepository extends JpaRepository<EntitlementGrant, Long> {
    Optional<EntitlementGrant> findBySourceTypeAndSourceIdAndResourceTypeAndResourceId(
            EntitlementSourceType sourceType,
            String sourceId,
            EntitlementResourceType resourceType,
            String resourceId
    );

    List<EntitlementGrant> findByUserIdAndStatusOrderByExpireAtAsc(Long userId, EntitlementStatus status);

    @Query("""
            select case when count(e) > 0 then true else false end
            from EntitlementGrant e
            where e.userId = :userId
              and e.resourceType = :resourceType
              and e.resourceId = :resourceId
              and e.status = :status
              and e.effectiveAt <= :effectiveAt
              and e.expireAt > :expireAt
            """)
    boolean existsActiveResourceGrantWithExpireAt(
            @Param("userId") Long userId,
            @Param("resourceType") EntitlementResourceType resourceType,
            @Param("resourceId") String resourceId,
            @Param("status") EntitlementStatus status,
            @Param("effectiveAt") LocalDateTime effectiveAt,
            @Param("expireAt") LocalDateTime expireAt
    );

    @Query("""
            select case when count(e) > 0 then true else false end
            from EntitlementGrant e
            where e.userId = :userId
              and e.resourceType = :resourceType
              and e.resourceId = :resourceId
              and e.status = :status
              and e.effectiveAt <= :effectiveAt
              and e.expireAt is null
            """)
    boolean existsActiveResourceGrantWithoutExpireAt(
            @Param("userId") Long userId,
            @Param("resourceType") EntitlementResourceType resourceType,
            @Param("resourceId") String resourceId,
            @Param("status") EntitlementStatus status,
            @Param("effectiveAt") LocalDateTime effectiveAt
    );

    @Query("""
            select case when count(e) > 0 then true else false end
            from EntitlementGrant e
            where e.userId = :userId
              and e.resourceType = :resourceType
              and e.status = :status
              and e.effectiveAt <= :effectiveAt
              and e.expireAt > :expireAt
            """)
    boolean existsActiveResourceTypeGrantWithExpireAt(
            @Param("userId") Long userId,
            @Param("resourceType") EntitlementResourceType resourceType,
            @Param("status") EntitlementStatus status,
            @Param("effectiveAt") LocalDateTime effectiveAt,
            @Param("expireAt") LocalDateTime expireAt
    );

    @Query("""
            select case when count(e) > 0 then true else false end
            from EntitlementGrant e
            where e.userId = :userId
              and e.resourceType = :resourceType
              and e.status = :status
              and e.effectiveAt <= :effectiveAt
              and e.expireAt is null
            """)
    boolean existsActiveResourceTypeGrantWithoutExpireAt(
            @Param("userId") Long userId,
            @Param("resourceType") EntitlementResourceType resourceType,
            @Param("status") EntitlementStatus status,
            @Param("effectiveAt") LocalDateTime effectiveAt
    );

    boolean existsByUserIdAndResourceTypeAndResourceIdAndStatusAndEffectiveAtLessThanEqualAndExpireAtGreaterThan(
            Long userId,
            EntitlementResourceType resourceType,
            String resourceId,
            EntitlementStatus status,
            LocalDateTime effectiveAt,
            LocalDateTime expireAt
    );

    boolean existsByUserIdAndResourceTypeAndResourceIdAndStatusAndEffectiveAtLessThanEqualAndExpireAtIsNull(
            Long userId,
            EntitlementResourceType resourceType,
            String resourceId,
            EntitlementStatus status,
            LocalDateTime effectiveAt
    );

    boolean existsByUserIdAndResourceTypeAndStatusAndEffectiveAtLessThanEqualAndExpireAtGreaterThan(
            Long userId,
            EntitlementResourceType resourceType,
            EntitlementStatus status,
            LocalDateTime effectiveAt,
            LocalDateTime expireAt
    );

    boolean existsByUserIdAndResourceTypeAndStatusAndEffectiveAtLessThanEqualAndExpireAtIsNull(
            Long userId,
            EntitlementResourceType resourceType,
            EntitlementStatus status,
            LocalDateTime effectiveAt
    );

    @Query("""
            select e
            from EntitlementGrant e
            where e.userId = :userId
              and e.status = :status
              and e.effectiveAt <= :effectiveAt
              and (e.expireAt is null or e.expireAt > :expireAt)
            order by e.expireAt asc, e.id asc
            """)
    List<EntitlementGrant> findActiveEntitlements(
            @Param("userId") Long userId,
            @Param("status") EntitlementStatus status,
            @Param("effectiveAt") LocalDateTime effectiveAt,
            @Param("expireAt") LocalDateTime expireAt
    );
}
