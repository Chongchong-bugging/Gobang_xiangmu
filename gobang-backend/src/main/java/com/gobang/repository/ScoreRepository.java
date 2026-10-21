package com.gobang.repository;

import com.gobang.entity.Score;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ScoreRepository extends JpaRepository<Score, Long> {

    Optional<Score> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Score s where s.userId = :userId")
    Optional<Score> findLockedByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Score s
               set s.wins = s.wins + :wins,
                   s.losses = s.losses + :losses,
                   s.draws = s.draws + :draws,
                   s.totalScore = s.totalScore + :scoreDelta
             where s.userId = :userId
            """)
    int applyResult(
            @Param("userId") Long userId,
            @Param("wins") int wins,
            @Param("losses") int losses,
            @Param("draws") int draws,
            @Param("scoreDelta") int scoreDelta);
}
