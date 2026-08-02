package com.fitlab.backend.service;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.Item;
import com.fitlab.backend.dto.RecommendationDto;
import com.fitlab.backend.matching.TagMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchServiceTest {

    private final MatchService matchService = new MatchService(new TagMatcher());

    private Item item(String name, Category category, Set<String> colors, Set<String> vibes) {
        return Item.builder().id(UUID.randomUUID()).name(name).category(category).colors(colors).vibes(vibes).build();
    }

    @Test
    void ranksCandidatesByDescendingScore() {
        Item anchor = item("anchor shirt", Category.SHIRT, Set.of("black", "red"), Set.of("street"));
        Item strongMatch = item("strong bottom", Category.BOTTOM, Set.of("black", "red"), Set.of("street"));
        Item weakMatch = item("weak bottom", Category.BOTTOM, Set.of("black"), Set.of());
        Item noMatch = item("no match bottom", Category.BOTTOM, Set.of("white"), Set.of("clean"));

        List<RecommendationDto> ranked = matchService.rank(UUID.randomUUID(), anchor, List.of(weakMatch, noMatch, strongMatch));

        assertThat(ranked).extracting(r -> r.item().name())
                .containsExactly("strong bottom", "weak bottom", "no match bottom");
        assertThat(ranked.get(0).score()).isEqualTo(8.0); // 2*3 + 1*2
        assertThat(ranked.get(2).score()).isZero();
    }

    @Test
    void excludesTheAnchorItselfFromResults() {
        Item anchor = item("anchor", Category.SHIRT, Set.of("black"), Set.of());

        List<RecommendationDto> ranked = matchService.rank(UUID.randomUUID(), anchor, List.of(anchor));

        assertThat(ranked).isEmpty();
    }
}
