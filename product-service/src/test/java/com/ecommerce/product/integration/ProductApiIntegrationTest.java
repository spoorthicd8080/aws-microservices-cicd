package com.ecommerce.product.integration;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProductApiIntegrationTest {

    private static final String ISSUER = "ecommerce-user-service";

    private static final KeyPair TEST_KEY_PAIR = generateKeyPair();

    private static final Path PUBLIC_KEY_PATH = createPublicKeyFile();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerJwtProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key-path", () -> PUBLIC_KEY_PATH.toString());
        registry.add("jwt.issuer", () -> ISSUER);
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate test RSA keys", e);
        }
    }

    private static Path createPublicKeyFile() {
        try {
            Path path = Files.createTempFile("product-test-public-key-", ".pem");

            String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'})
                    .encodeToString(TEST_KEY_PAIR.getPublic().getEncoded());

            Files.writeString(
                    path,
                    "-----BEGIN PUBLIC KEY-----\n"
                            + encoded
                            + "\n-----END PUBLIC KEY-----\n"
            );

            return path;
        } catch (Exception e) {
            throw new IllegalStateException("Could not create test public key", e);
        }
    }

    private String adminToken() {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .issuer(ISSUER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .signWith(TEST_KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String createCategory(String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Integration test category"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.id");
    }

    @Test
    void productLifecycle_createSearchLookupDeactivate() throws Exception {
        String categoryId = createCategory("Apparel-" + UUID.randomUUID());
        String sku = "SKU-" + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase();

        String body = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":"Black cotton tee","description":"180gsm",
                                 "price":24.99,"currency":"CAD","categoryId":"%s"}
                                """.formatted(sku, categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String productId = JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/v1/products")
                        .param("q", "cotton")
                        .param("categoryId", categoryId)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sku").value(sku));

        mockMvc.perform(get("/api/v1/products/lookup")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("ids", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(24.99))
                .andExpect(jsonPath("$[0].currency").value("CAD"))
                .andExpect(jsonPath("$[0].description").doesNotExist());

        mockMvc.perform(delete("/api/v1/products/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void createProduct_inInactiveCategory_returns422() throws Exception {
        String categoryId = createCategory("Retired-" + UUID.randomUUID());

        mockMvc.perform(delete("/api/v1/categories/{id}", categoryId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-INACTIVE-CAT","name":"Orphan","price":9.99,
                                 "currency":"CAD","categoryId":"%s"}
                                """.formatted(categoryId)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void duplicateSku_returns409() throws Exception {
        String categoryId = createCategory("Dup-" + UUID.randomUUID());

        String sku = "SKU-DUPE-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        String payload = """
                {"sku":"%s","name":"First","price":10.00,"currency":"CAD","categoryId":"%s"}
                """.formatted(sku, categoryId);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }
}