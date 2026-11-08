package com.ecommerce.order.integration;

import com.ecommerce.order.client.ProductServiceClient;
import com.ecommerce.order.client.UserServiceClient;
import com.ecommerce.order.client.dto.AddressDto;
import com.ecommerce.order.client.dto.CustomerDto;
import com.ecommerce.order.client.dto.ProductDto;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderApiIntegrationTest {

    private static final String JWT_ISSUER = "ecommerce-user-service";

    private static final KeyPair TEST_KEY_PAIR = generateTestKeyPair();

    private static final Path PUBLIC_KEY_FILE = createPublicKeyFile();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureJwtProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.issuer", () -> JWT_ISSUER);
        registry.add("jwt.public-key-path", () -> PUBLIC_KEY_FILE.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserServiceClient userServiceClient;

    @MockitoBean
    private ProductServiceClient productServiceClient;

    private final UUID customerId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @Test
    void orderLifecycle_placeReadAdvanceThroughStatuses() throws Exception {
        givenHealthyDownstreams();

        String body = mockMvc.perform(post("/api/v1/orders")
                        .with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartJson(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(49.98))
                .andReturn().getResponse().getContentAsString();

        String orderId = JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productSku").value("SKU-A"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(24.99))
                .andExpect(jsonPath("$.shippingAddress.city").value("Toronto"));

        mockMvc.perform(get("/api/v1/customers/{customerId}/orders", customerId)
                        .with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].items").doesNotExist());

        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DELIVERED"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelBeforePayment_succeeds_andIsIdempotent() throws Exception {
        givenHealthyDownstreams();

        String body = mockMvc.perform(post("/api/v1/orders")
                        .with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartJson(1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String orderId = JsonPath.read(body, "$.id");

        mockMvc.perform(patch("/api/v1/orders/{id}/cancel", orderId)
                        .with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(patch("/api/v1/orders/{id}/cancel", orderId)
                        .with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void placingAnOrderForADiscontinuedProduct_returns422_andPersistsNothing()
            throws Exception {

        when(userServiceClient.getCustomer(any()))
                .thenReturn(new CustomerDto(customerId, "ACTIVE"));

        when(userServiceClient.getAddress(any(), any()))
                .thenReturn(address());

        when(productServiceClient.lookupProducts(anyList()))
                .thenReturn(List.of(new ProductDto(
                        productId, "SKU-A", "Tee",
                        new BigDecimal("24.99"), "CAD", "INACTIVE")));

        mockMvc.perform(post("/api/v1/orders")
                        .with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartJson(1)))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/v1/customers/{customerId}/orders", customerId)
                        .with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    private RequestPostProcessor authenticated() {
        String token = Jwts.builder()
                .subject(customerId.toString())
                .claim("role", "ADMIN")
                .issuer(JWT_ISSUER)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(TEST_KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();

        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    private void givenHealthyDownstreams() {
        when(userServiceClient.getCustomer(any()))
                .thenReturn(new CustomerDto(customerId, "ACTIVE"));

        when(userServiceClient.getAddress(any(), any()))
                .thenReturn(address());

        when(productServiceClient.lookupProducts(anyList()))
                .thenReturn(List.of(new ProductDto(
                        productId, "SKU-A", "Tee",
                        new BigDecimal("24.99"), "CAD", "ACTIVE")));
    }

    private AddressDto address() {
        return new AddressDto(
                addressId, customerId, "100 King St W", null,
                "Toronto", "ON", "M5X 1A9", "CA");
    }

    private String cartJson(int quantity) {
        return """
                {"customerId":"%s","shippingAddressId":"%s",
                 "items":[{"productId":"%s","quantity":%d}]}
                """.formatted(customerId, addressId, productId, quantity);
    }

    private static KeyPair generateTestKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not generate test RSA key pair", exception);
        }
    }

    private static Path createPublicKeyFile() {
        try {
            RSAPublicKey publicKey = (RSAPublicKey) TEST_KEY_PAIR.getPublic();

            String pem = """
                    -----BEGIN PUBLIC KEY-----
                    %s
                    -----END PUBLIC KEY-----
                    """.formatted(Base64.getMimeEncoder(64, new byte[]{'\n'})
                    .encodeToString(publicKey.getEncoded()));

            Path file = Files.createTempFile("order-test-public-key-", ".pem");
            Files.writeString(file, pem);

            return file;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create test public key file", exception);
        }
    }
}