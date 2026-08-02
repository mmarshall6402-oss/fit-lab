package com.fitlab.backend;

import com.fitlab.backend.dto.AuthResponse;
import com.fitlab.backend.dto.CreateItemRequest;
import com.fitlab.backend.dto.ItemDto;
import com.fitlab.backend.dto.OutfitDto;
import com.fitlab.backend.dto.RegisterRequest;
import com.fitlab.backend.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FitLabApplicationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private HttpHeaders authHeaders;

    @BeforeEach
    void registerUser() {
        RegisterRequest request = new RegisterRequest("fitlab-app-test-" + UUID.randomUUID() + "@example.com", "password123");
        AuthResponse auth = restTemplate.postForEntity(url("/auth/register"), request, AuthResponse.class).getBody();
        authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(auth.token());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private <T> ResponseEntity<T> post(String path, Object body, Class<T> responseType) {
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, authHeaders), responseType);
    }

    private <T> ResponseEntity<T> get(String path, Class<T> responseType) {
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(authHeaders), responseType);
    }

    private void delete(String path) {
        restTemplate.exchange(url(path), HttpMethod.DELETE, new HttpEntity<>(authHeaders), Void.class);
    }

    @Test
    void createAndDeleteItemRoundTrips() {
        CreateItemRequest request = new CreateItemRequest("Test Jacket", Category.SHIRT, null, Set.of("black"), Set.of("street"));

        ResponseEntity<ItemDto> created = post("/items", request, ItemDto.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody().id()).isNotNull();

        delete("/items/" + created.getBody().id());

        ResponseEntity<ItemDto[]> afterDelete = get("/items?category=SHIRT", ItemDto[].class);
        assertThat(List.of(afterDelete.getBody())).extracting(ItemDto::id).doesNotContain(created.getBody().id());
    }

    @Test
    void updateItemEditsNameAndTags() {
        CreateItemRequest request = new CreateItemRequest("Draft Jacket", Category.SHIRT, null, Set.of("black"), Set.of("street"));
        ItemDto created = post("/items", request, ItemDto.class).getBody();

        CreateItemRequest edit = new CreateItemRequest("Final Jacket", Category.SHIRT, null, Set.of("red", "black"), Set.of("loud"));
        ResponseEntity<ItemDto> response = restTemplate.exchange(
                url("/items/" + created.id()), HttpMethod.PUT, new HttpEntity<>(edit, authHeaders), ItemDto.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().name()).isEqualTo("Final Jacket");
        assertThat(response.getBody().colors()).containsExactlyInAnyOrder("red", "black");
        assertThat(response.getBody().vibes()).containsExactly("loud");

        delete("/items/" + created.id());
    }

    @Test
    void buildsBestOutfitFromCatalog() {
        ItemDto anchor = createItem("Test Shirt", Category.SHIRT, Set.of("black"), Set.of("street"));
        createItem("Test Bottom", Category.BOTTOM, Set.of("black"), Set.of("street"));
        createItem("Test Shoes", Category.SHOES, Set.of("black"), Set.of("street"));

        ResponseEntity<OutfitDto> response = get("/outfit/build?anchorId=" + anchor.id(), OutfitDto.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        OutfitDto outfit = response.getBody();
        assertThat(outfit.shirt().id()).isEqualTo(anchor.id());
        assertThat(outfit.score()).isBetween(0.0, 100.0);
        assertThat(outfit.reasons()).isNotEmpty();
    }

    private ItemDto createItem(String name, Category category, Set<String> colors, Set<String> vibes) {
        CreateItemRequest request = new CreateItemRequest(name, category, null, colors, vibes);
        return post("/items", request, ItemDto.class).getBody();
    }
}
