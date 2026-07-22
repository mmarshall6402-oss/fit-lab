package com.fitlab.backend.matching;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.Item;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TagMatcherTest {

    private final TagMatcher matcher = new TagMatcher();

    private Item item(Category category, Set<String> colors, Set<String> vibes) {
        return Item.builder()
                .id(UUID.randomUUID())
                .name("test")
                .category(category)
                .colors(colors)
                .vibes(vibes)
                .build();
    }

    @Test
    void scoresColorOverlapWithWeightThree() {
        Item a = item(Category.SHIRT, Set.of("black"), Set.of());
        Item b = item(Category.BOTTOM, Set.of("black"), Set.of());

        assertThat(matcher.score(a, b)).isEqualTo(3.0);
    }

    @Test
    void scoresVibeOverlapWithWeightTwo() {
        Item a = item(Category.SHIRT, Set.of(), Set.of("street"));
        Item b = item(Category.BOTTOM, Set.of(), Set.of("street"));

        assertThat(matcher.score(a, b)).isEqualTo(2.0);
    }

    @Test
    void combinesColorAndVibeOverlap() {
        Item a = item(Category.SHIRT, Set.of("black", "red"), Set.of("street", "loud"));
        Item b = item(Category.BOTTOM, Set.of("black", "red"), Set.of("street", "loud"));

        // overlap(colors)=2 * 3 + overlap(vibes)=2 * 2 = 6 + 4 = 10
        assertThat(matcher.score(a, b)).isEqualTo(10.0);
    }

    @Test
    void zeroWhenNoSharedTags() {
        Item a = item(Category.SHIRT, Set.of("black"), Set.of("street"));
        Item b = item(Category.BOTTOM, Set.of("white"), Set.of("clean"));

        assertThat(matcher.score(a, b)).isZero();
    }

    @Test
    void zeroWhenEitherSideHasNoTags() {
        Item a = item(Category.SHIRT, Set.of(), Set.of());
        Item b = item(Category.BOTTOM, Set.of("black"), Set.of("street"));

        assertThat(matcher.score(a, b)).isZero();
    }

    @Test
    void isSymmetric() {
        Item a = item(Category.SHIRT, Set.of("black", "grey"), Set.of("street"));
        Item b = item(Category.BOTTOM, Set.of("black"), Set.of("street", "loud"));

        assertThat(matcher.score(a, b)).isEqualTo(matcher.score(b, a));
    }
}
