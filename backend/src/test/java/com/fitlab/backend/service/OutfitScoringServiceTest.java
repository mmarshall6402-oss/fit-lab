package com.fitlab.backend.service;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.Item;
import com.fitlab.backend.matching.TagMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutfitScoringServiceTest {

    private final OutfitScoringService scoringService = new OutfitScoringService(new TagMatcher());

    private Item item(String name, Category category, Set<String> colors, Set<String> vibes) {
        return Item.builder().id(UUID.randomUUID()).name(name).category(category).colors(colors).vibes(vibes).build();
    }

    @Test
    void perfectlyAlignedOutfitScoresMaximum() {
        Item shirt = item("shirt", Category.SHIRT, Set.of("black", "red"), Set.of("street"));
        Item bottom = item("bottom", Category.BOTTOM, Set.of("black", "red"), Set.of("street"));
        Item shoes = item("shoes", Category.SHOES, Set.of("black", "red"), Set.of("street"));

        assertThat(scoringService.holisticScore(shirt, bottom, shoes)).isEqualTo(100.0);
    }

    @Test
    void noSharedTagsScoresZero() {
        Item shirt = item("shirt", Category.SHIRT, Set.of("black"), Set.of("street"));
        Item bottom = item("bottom", Category.BOTTOM, Set.of("white"), Set.of("clean"));
        Item shoes = item("shoes", Category.SHOES, Set.of("green"), Set.of("quiet"));

        assertThat(scoringService.holisticScore(shirt, bottom, shoes)).isZero();
    }

    @Test
    void scoreIsAlwaysWithinZeroToHundred() {
        Item shirt = item("shirt", Category.SHIRT, Set.of("black", "red", "blue"), Set.of("street", "loud"));
        Item bottom = item("bottom", Category.BOTTOM, Set.of("black"), Set.of());
        Item shoes = item("shoes", Category.SHOES, Set.of(), Set.of("street"));

        double score = scoringService.holisticScore(shirt, bottom, shoes);

        assertThat(score).isBetween(0.0, 100.0);
    }

    @Test
    void betterAlignedOutfitOutscoresWeakerOne() {
        Item shirt = item("shirt", Category.SHIRT, Set.of("black", "red"), Set.of("street"));
        Item strongBottom = item("strongBottom", Category.BOTTOM, Set.of("black", "red"), Set.of("street"));
        Item weakBottom = item("weakBottom", Category.BOTTOM, Set.of("green"), Set.of());
        Item shoes = item("shoes", Category.SHOES, Set.of("black"), Set.of("street"));

        double strongScore = scoringService.holisticScore(shirt, strongBottom, shoes);
        double weakScore = scoringService.holisticScore(shirt, weakBottom, shoes);

        assertThat(strongScore).isGreaterThan(weakScore);
    }

    @Test
    void reasonsIncludeSharedVibeAcrossAllThreePieces() {
        Item shirt = item("shirt", Category.SHIRT, Set.of("black"), Set.of("street"));
        Item bottom = item("bottom", Category.BOTTOM, Set.of("grey"), Set.of("street"));
        Item shoes = item("shoes", Category.SHOES, Set.of("white"), Set.of("street"));

        List<String> reasons = scoringService.reasons(shirt, bottom, shoes);

        assertThat(reasons).anyMatch(r -> r.contains("share the 'street' vibe"));
    }

    @Test
    void reasonsFlagNeutralBottomWhenAnchorHasManyColors() {
        Item shirt = item("shirt", Category.SHIRT, Set.of("black", "red", "blue"), Set.of());
        Item bottom = item("bottom", Category.BOTTOM, Set.of("black"), Set.of());
        Item shoes = item("shoes", Category.SHOES, Set.of("orange"), Set.of());

        List<String> reasons = scoringService.reasons(shirt, bottom, shoes);

        assertThat(reasons).anyMatch(r -> r.contains("bottom stays neutral"));
    }

    @Test
    void reasonsNeverClaimSharedVibeWhenThereIsNone() {
        Item shirt = item("shirt", Category.SHIRT, Set.of("black"), Set.of("street"));
        Item bottom = item("bottom", Category.BOTTOM, Set.of("white"), Set.of("clean"));
        Item shoes = item("shoes", Category.SHOES, Set.of("green"), Set.of("quiet"));

        List<String> reasons = scoringService.reasons(shirt, bottom, shoes);

        assertThat(reasons).noneMatch(r -> r.contains("share the"));
        assertThat(reasons).isNotEmpty(); // falls back to an honest "best available" reason
    }
}
