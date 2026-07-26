package com.fitlab.backend;

import com.fitlab.backend.dto.CreateItemRequest;
import com.fitlab.backend.dto.ItemDto;
import com.fitlab.backend.dto.OutfitDto;
import com.fitlab.backend.domain.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FitLabApplicationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void catalogIsSeededOnStartup() {
        ResponseEntity<ItemDto[]> response = restTemplate.getForEntity(url("/items"), ItemDto[].class);

        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody()).extracting(ItemDto::category).contains(Category.SHIRT, Category.BOTTOM, Category.SHOES);
    }

    @Test
    void createAndDeleteItemRoundTrips() {
        CreateItemRequest request = new CreateItemRequest("Test Jacket", Category.SHIRT, null, Set.of("black"), Set.of("street"));

        ResponseEntity<ItemDto> created = restTemplate.postForEntity(url("/items"), request, ItemDto.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody().id()).isNotNull();

        restTemplate.delete(url("/items/" + created.getBody().id()));

        ResponseEntity<ItemDto[]> afterDelete = restTemplate.getForEntity(url("/items?category=SHIRT"), ItemDto[].class);
        assertThat(List.of(afterDelete.getBody())).extracting(ItemDto::id).doesNotContain(created.getBody().id());
    }

    @Test
    void updateItemEditsNameAndTags() {
        CreateItemRequest request = new CreateItemRequest("Draft Jacket", Category.SHIRT, null, Set.of("black"), Set.of("street"));
        ItemDto created = restTemplate.postForEntity(url("/items"), request, ItemDto.class).getBody();

        CreateItemRequest edit = new CreateItemRequest("Final Jacket", Category.SHIRT, null, Set.of("red", "black"), Set.of("loud"));
        ResponseEntity<ItemDto> response = restTemplate.exchange(
                url("/items/" + created.id()), HttpMethod.PUT, new HttpEntity<>(edit), ItemDto.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().name()).isEqualTo("Final Jacket");
        assertThat(response.getBody().colors()).containsExactlyInAnyOrder("red", "black");
        assertThat(response.getBody().vibes()).containsExactly("loud");

        restTemplate.delete(url("/items/" + created.id()));
    }

    @Test
    void buildsBestOutfitFromSeededCatalog() {
        ItemDto[] shirts = restTemplate.getForEntity(url("/items?category=SHIRT"), ItemDto[].class).getBody();
        ItemDto anchor = shirts[0];

        ResponseEntity<OutfitDto> response = restTemplate.getForEntity(url("/outfit/build?anchorId=" + anchor.id()), OutfitDto.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        OutfitDto outfit = response.getBody();
        assertThat(outfit.shirt().id()).isEqualTo(anchor.id());
        assertThat(outfit.score()).isBetween(0.0, 100.0);
        assertThat(outfit.reasons()).isNotEmpty();
    }
}
