package com.gobang.repository;

import com.gobang.entity.GameResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameResultRepository extends JpaRepository<GameResult, String> {
}
