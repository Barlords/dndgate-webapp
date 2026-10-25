package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.bootstrap.DndGateApiApplication;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import fr.barlords.dndgateapi.domain.port.out.PaymentProviderSpi;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Boots the whole application against a real PostgreSQL (Flyway migrations included)
 * and exposes small helpers to drive it over HTTP like a real client would.
 */
@SpringBootTest(
        classes = DndGateApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "security.jwt.secret=integration-test-secret-integration-test-secret",
                // the probes get a free port so that tests never meet a running application
                "management.server.port=0",
                // the tests log in and register many accounts from the same address
                "security.rate-limit.login.max=1000000",
                "security.rate-limit.refresh.max=1000000",
                "security.rate-limit.register.max=1000000",
                "security.rate-limit.verify.max=1000000",
                "security.rate-limit.resend.max=1000000",
                "plans.file=classpath:plans/test-plans.json"})
@ActiveProfiles("dev")
abstract class ApiIntegrationTestSupport {

    // One container shared by every test class: the Spring context is cached across classes
    // and must keep pointing to a database that stays alive.
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean
    EmailingSpi emailingSpi;

    @MockitoBean
    MinioSpi minioSpi;

    /**
     * Never Stripe in these tests. Unstubbed, the mock says billing is disabled (isEnabled false) and finds nothing,
     * like the application without a Stripe key; the billing tests stub what they need (reset after each test).
     */
    @MockitoBean
    PaymentProviderSpi paymentProviderSpi;

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    final JsonMapper json = JsonMapper.builder().build();

    RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port + "/dndgate-api")
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                .build();
    }

    ResponseEntity<String> call(HttpMethod method, String path, String token, Object body) {
        RestClient.RequestBodySpec spec = client().method(method).uri(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            spec.headers(headers -> headers.setBearerAuth(token));
        }
        if (body != null) {
            spec.body(body);
        }
        return spec.retrieve().toEntity(String.class);
    }

    JsonNode body(ResponseEntity<String> response) {
        return json.readTree(response.getBody());
    }

    /** A registered, activated account and the JWT obtained by logging in. */
    record TestAccount(UUID id, String pseudo, String password, String token, String refreshToken) { }

    TestAccount registerAndLogin() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String pseudo = "account" + suffix;
        String password = "password-" + suffix;

        createAccount(pseudo, pseudo + "@test.fr", password);

        ResponseEntity<String> login = call(HttpMethod.POST, "/accounts/login", null, Map.of("pseudo", pseudo, "password", password));
        Assertions.assertEquals(200, login.getStatusCode().value(), login.getBody());

        JsonNode node = body(login);
        return new TestAccount(UUID.fromString(node.get("accountId").asString()), pseudo, password,
                node.get("token").asString(), node.get("refreshToken").asString());
    }

    /**
     * The real signup flow, without the login: POST /accounts, the clear code read from the mail the mock "sent", then
     * POST /accounts/verify. Every test that needs an account goes through it, so that the regression net keeps
     * exercising the signup end to end.
     */
    void createAccount(String pseudo, String email, String password) {
        Assertions.assertEquals(202, call(HttpMethod.POST, "/accounts", null,
                Map.of("email", email, "password", password, "pseudo", pseudo)).getStatusCode().value());
        Assertions.assertEquals(204, verifyEmail(email, lastSignupCode(email)));
    }

    /** The clear code of the last confirmation mail that was sent to the address (the only place it exists). */
    String lastSignupCode(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        Mockito.verify(emailingSpi, Mockito.atLeastOnce())
                .sendSignupConfirmationEmail(Mockito.argThat(signup -> signup.getEmail().equals(email)), code.capture());
        return code.getAllValues().getLast();
    }

    /** What the page behind the link of the verification mail does. */
    int verifyEmail(String email, String code) {
        return call(HttpMethod.POST, "/accounts/verify", null, Map.of("email", email, "code", code))
                .getStatusCode().value();
    }

    /** Logs the account in again: a new session, independent from the previous ones. */
    TestAccount login(TestAccount account) {
        ResponseEntity<String> login = call(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", account.pseudo(), "password", account.password()));
        Assertions.assertEquals(200, login.getStatusCode().value(), login.getBody());
        JsonNode node = body(login);
        return new TestAccount(account.id(), account.pseudo(), account.password(),
                node.get("token").asString(), node.get("refreshToken").asString());
    }

    /** Deletes the account of the caller, confirming with its password (what the API asks for). */
    int deleteAccount(TestAccount account) {
        return call(HttpMethod.DELETE, "/accounts", account.token(), Map.of("confirmation", account.password()))
                .getStatusCode().value();
    }

    /** The signature of a PNG file followed by a few bytes: all that is looked at. */
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    ResponseEntity<String> createCharacter(TestAccount account, UUID adventureId, String name) {
        return createCharacter(account, adventureId, name, PNG, MediaType.IMAGE_PNG);
    }

    /** A character sent without the picture part: the picture is optional. */
    ResponseEntity<String> createCharacterWithoutPicture(TestAccount account, UUID adventureId, String name) {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> request = new LinkedHashMap<>(Map.of("name", name, "race", "Human", "martialClass", "Wizard",
                "personality", "Wise", "background", "Old"));
        if (adventureId != null) {
            request.put("adventureId", adventureId.toString());
        }
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("createGameCharacterRequest", new HttpEntity<>(json.writeValueAsString(request), requestHeaders));

        return client().post().uri("/game-characters")
                .headers(headers -> headers.setBearerAuth(account.token()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve().toEntity(String.class);
    }

    ResponseEntity<String> createCharacter(TestAccount account, UUID adventureId, String name, byte[] content, MediaType declaredType) {
        return createCharacter(account, adventureId, name, content, declaredType, "Old");
    }

    ResponseEntity<String> createCharacter(TestAccount account, UUID adventureId, String name, byte[] content, MediaType declaredType,
                                           String background) {
        HttpHeaders pictureHeaders = new HttpHeaders();
        pictureHeaders.setContentType(declaredType);
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);

        ByteArrayResource picture = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "picture.png";
            }
        };
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("picturePresentation", new HttpEntity<>(picture, pictureHeaders));
        Map<String, String> request = new LinkedHashMap<>(Map.of("name", name, "race", "Human", "martialClass", "Wizard",
                "personality", "Wise", "background", background));
        // Without an adventure, the character is kept outside any adventure of the application
        if (adventureId != null) {
            request.put("adventureId", adventureId.toString());
        }
        parts.add("createGameCharacterRequest", new HttpEntity<>(json.writeValueAsString(request), requestHeaders));

        return client().post().uri("/game-characters")
                .headers(headers -> headers.setBearerAuth(account.token()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve().toEntity(String.class);
    }

    /** Gives the account an active subscription to the plan, replacing any previous subscription row. */
    void subscribe(TestAccount account, String planId) {
        jdbc.update("insert into subscription (account_id, stripe_customer_id, plan_id, status, cancel_at_period_end, updated_at) "
                + "values (?, ?, ?, 'ACTIVE', false, now()) "
                + "on conflict (account_id) do update set plan_id = excluded.plan_id, status = 'ACTIVE', updated_at = now()",
                account.id(), "cus_test_" + account.id(), planId);
    }

    UUID createAdventure(TestAccount owner, String visibility) {
        ResponseEntity<String> response = call(HttpMethod.POST, "/adventures", owner.token(), Map.of(
                "name", "Quest", "description", "A quest", "maxPlayer", 4,
                "visibility", visibility, "postalCode", "75001", "city", "Paris"));
        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
        return UUID.fromString(body(response).get("id").asString());
    }
}
