package com.fitlab.backend.service;

import com.fitlab.backend.domain.Item;
import com.fitlab.backend.dto.ItemDto;
import com.fitlab.backend.dto.RecommendationDto;
import com.fitlab.backend.matching.Matcher;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Ranks candidate items against an anchor item using the injected Matcher strategy.
 */
@Service
public class MatchService {

    private final Matcher matcher;

    public MatchService(Matcher matcher) {
        this.matcher = matcher;
    }

    public List<RecommendationDto> rank(Item anchor, List<Item> candidates) {
        return candidates.stream()
                .filter(candidate -> !candidate.getId().equals(anchor.getId()))
                .map(candidate -> new RecommendationDto(ItemDto.from(candidate), matcher.score(anchor, candidate)))
                .sorted(Comparator.comparingDouble(RecommendationDto::score).reversed())
                .toList();
    }
}
