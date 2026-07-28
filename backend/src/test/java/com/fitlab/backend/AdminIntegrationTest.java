package com.fitlab.backend;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.dto.CreateItemRequest;
import com.fitlab.backend.dto.ItemDto;
import com.fitlab.backend.dto.OutfitDto;
import com.fitlab.backend.dto.ScoringConfigDto;
import com.fitlab.backend.dto.UpdateScoringConfigRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminIntegrationTest {

    private static final String ADMIN_TOKEN = "test-admin-token"; // matches src/test/resources/application.yml

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<Object> withToken(Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.set("X-Admin-Token", token);
        }
        return new HttpEntity<>(body, headers);
    }

    @Test
    void scoringConfigEndpointsRejectMissingOrWrongToken() {
        ResponseEntity<String> noToken = restTemplate.exchange(
                url("/admin/scoring-config"), HttpMethod.GET, withToken(null, null), String.class);
        assertThat(noToken.getStatusCode().value()).isEqualTo(401);

        ResponseEntity<String> wrongToken = restTemplate.exchange(
                url("/admin/scoring-config"), HttpMethod.GET, withToken(null, "nope"), String.class);
        assertThat(wrongToken.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void updatingScoringConfigTakesEffectLiveOnTheNextScore() {
        ItemDto shirt = createItem("Admin Test Shirt", Category.SHIRT, Set.of("black"), Set.of());
        ItemDto bottom = createItem("Admin Test Bottom", Category.BOTTOM, Set.of("black"), Set.of());
        ItemDto shoes = createItem("Admin Test Shoes", Category.SHOES, Set.of("white"), Set.of());

        // Only shirt/bottom share a color; with default weights this should be a partial (non-zero, non-perfect) score.
        OutfitDto before = restTemplate.getForEntity(
                url("/outfit/score?shirtId=" + shirt.id() + "&bottomId=" + bottom.id() + "&shoesId=" + shoes.id()),
                OutfitDto.class).getBody();
        assertThat(before.score()).isBetween(0.0, 100.0);

        UpdateScoringConfigRequest request = new UpdateScoringConfigRequest(
                3.0, 2.0, 3,
                Set.of("black", "white", "grey", "gray", "beige", "tan", "navy", "cream", "khaki"),
                true, true, true, 2.0);
        ResponseEntity<ScoringConfigDto> updateResponse = restTemplate.exchange(
                url("/admin/scoring-config"), HttpMethod.PUT, withToken(request, ADMIN_TOKEN), ScoringConfigDto.class);
        assertThat(updateResponse.getStatusCode().is2xxSuccessful()).isTrue();

        // Zeroing the color weight should collapse the shared-color pair to zero immediately, no restart needed.
        UpdateScoringConfigRequest zeroedColor = new UpdateScoringConfigRequest(
                0.0, 2.0, 3,
                Set.of("black", "white", "grey", "gray", "beige", "tan", "navy", "cream", "khaki"),
                true, true, true, 2.0);
        restTemplate.exchange(url("/admin/scoring-config"), HttpMethod.PUT, withToken(zeroedColor, ADMIN_TOKEN), ScoringConfigDto.class);

        OutfitDto after = restTemplate.getForEntity(
                url("/outfit/score?shirtId=" + shirt.id() + "&bottomId=" + bottom.id() + "&shoesId=" + shoes.id()),
                OutfitDto.class).getBody();
        assertThat(after.score()).isZero();

        // Restore defaults so this test doesn't leak state into other tests sharing the same H2 file/context.
        restTemplate.exchange(url("/admin/scoring-config/reset"), HttpMethod.POST, withToken(null, ADMIN_TOKEN), ScoringConfigDto.class);
    }

    @Test
    void wipeItemsRequiresTokenAndClearsTheCatalog() {
        createItem("Wipe Test Shirt", Category.SHIRT, Set.of("black"), Set.of());

        ResponseEntity<String> noToken = restTemplate.exchange(
                url("/admin/items"), HttpMethod.DELETE, withToken(null, null), String.class);
        assertThat(noToken.getStatusCode().value()).isEqualTo(401);

        ResponseEntity<Void> wiped = restTemplate.exchange(
                url("/admin/items"), HttpMethod.DELETE, withToken(null, ADMIN_TOKEN), Void.class);
        assertThat(wiped.getStatusCode().value()).isEqualTo(204);

        ResponseEntity<ItemDto[]> remaining = restTemplate.getForEntity(url("/items"), ItemDto[].class);
        assertThat(remaining.getBody()).isEmpty();
    }

    private ItemDto createItem(String name, Category category, Set<String> colors, Set<String> vibes) {
        CreateItemRequest request = new CreateItemRequest(name, category, null, colors, vibes);
        return restTemplate.postForEntity(url("/items"), request, ItemDto.class).getBody();
    }
}
