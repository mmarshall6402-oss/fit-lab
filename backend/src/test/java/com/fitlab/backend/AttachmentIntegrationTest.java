package com.fitlab.backend;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.dto.AttachmentDto;
import com.fitlab.backend.dto.AuthResponse;
import com.fitlab.backend.dto.CreateItemRequest;
import com.fitlab.backend.dto.ItemDto;
import com.fitlab.backend.dto.RegisterRequest;
import com.fitlab.backend.repository.AttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AttachmentIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AttachmentRepository attachmentRepository;

    private HttpHeaders authHeaders;

    @BeforeEach
    void registerUser() {
        RegisterRequest request = new RegisterRequest("fitlab-attachment-test-" + UUID.randomUUID() + "@example.com", "password123");
        AuthResponse auth = restTemplate.postForEntity(url("/auth/register"), request, AuthResponse.class).getBody();
        authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(auth.token());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ItemDto createItem() {
        CreateItemRequest request = new CreateItemRequest("Receipt Test Shirt", Category.SHIRT, null, Set.of("black"), Set.of("street"));
        return restTemplate.exchange(url("/items"), HttpMethod.POST, new HttpEntity<>(request, authHeaders), ItemDto.class).getBody();
    }

    private HttpEntity<MultiValueMap<String, Object>> multipartFile(String filename, String contentType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource("test-content".getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        body.add("file", resource);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.addAll(authHeaders);
        return new HttpEntity<>(body, headers);
    }

    private <T> ResponseEntity<T> get(String path, Class<T> responseType) {
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(authHeaders), responseType);
    }

    private void delete(String path) {
        restTemplate.exchange(url(path), HttpMethod.DELETE, new HttpEntity<>(authHeaders), Void.class);
    }

    @Test
    void uploadsListsAndDeletesAnAttachment() {
        ItemDto item = createItem();

        ResponseEntity<AttachmentDto> uploaded = restTemplate.postForEntity(
                url("/items/" + item.id() + "/attachments"), multipartFile("receipt.pdf", "application/pdf"), AttachmentDto.class);
        assertThat(uploaded.getStatusCode().value()).isEqualTo(201);
        assertThat(uploaded.getBody().filename()).isEqualTo("receipt.pdf");
        assertThat(uploaded.getBody().url()).startsWith("/uploads/");

        ResponseEntity<AttachmentDto[]> listed = get("/items/" + item.id() + "/attachments", AttachmentDto[].class);
        assertThat(listed.getBody()).hasSize(1);
        assertThat(listed.getBody()[0].id()).isEqualTo(uploaded.getBody().id());

        delete("/items/" + item.id() + "/attachments/" + uploaded.getBody().id());

        ResponseEntity<AttachmentDto[]> afterDelete = get("/items/" + item.id() + "/attachments", AttachmentDto[].class);
        assertThat(afterDelete.getBody()).isEmpty();
    }

    @Test
    void deletingAnItemCascadesToItsAttachments() {
        ItemDto item = createItem();
        ResponseEntity<AttachmentDto> uploaded = restTemplate.postForEntity(
                url("/items/" + item.id() + "/attachments"), multipartFile("size-chart.png", "image/png"), AttachmentDto.class);
        assertThat(uploaded.getStatusCode().value()).isEqualTo(201);

        delete("/items/" + item.id());

        assertThat(attachmentRepository.existsById(uploaded.getBody().id())).isFalse();
    }

    @Test
    void uploadingToAMissingItemReturnsNotFound() {
        ResponseEntity<AttachmentDto> response = restTemplate.postForEntity(
                url("/items/" + java.util.UUID.randomUUID() + "/attachments"), multipartFile("f.txt", "text/plain"), AttachmentDto.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
