package com.gobang.service;

import com.gobang.dto.ScoreDTO;
import com.gobang.entity.Score;
import com.gobang.entity.UserInfo;
import com.gobang.repository.ScoreRepository;
import com.gobang.repository.UserInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ScoreService {

    @Autowired private ScoreRepository scoreRepo;
    @Autowired private UserInfoRepository userRepo;

    /**
     * 查询个人得分
     */
    public ScoreDTO getMyScore(Long userId) {
        Optional<Score> optScore = scoreRepo.findByUserId(userId);
        Optional<UserInfo> optUser = userRepo.findById(userId);

        if (optUser.isEmpty()) return null;

        ScoreDTO dto = new ScoreDTO();
        dto.setNickname(optUser.get().getNickname());

        if (optScore.isPresent()) {
            Score s = optScore.get();
            dto.setTotalScore(s.getTotalScore());
            dto.setWins(s.getWins());
            dto.setLosses(s.getLosses());
            dto.setDraws(s.getDraws());

            int total = s.getWins() + s.getLosses() + s.getDraws();
            dto.setWinRate(total > 0 ? (double) s.getWins() / total * 100 : 0);
        }

        return dto;
    }
}
