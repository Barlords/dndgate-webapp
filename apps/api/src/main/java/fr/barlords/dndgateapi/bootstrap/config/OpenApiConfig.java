package fr.barlords.dndgateapi.bootstrap.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Set;

/**
 * The OpenAPI description of the API ({@code contracts/openapi.yaml}, from which the Angular client is generated).
 * Everything that cannot be read from the controllers is said here: the bearer token, which routes need none, and
 * the one shape every error has.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";

    static final String PROBLEM = "Problem";

    /** Version of the contract (not of the build): raised when the routes change in a way clients must follow. */
    static final String CONTRACT_VERSION = "2.0.0";

    @Bean
    public OpenAPI dndGateOpenApi(@Value("${server.servlet.context-path:}") String contextPath) {
        return new OpenAPI()
                .info(new Info()
                        .title("DnDGate API")
                        .version(CONTRACT_VERSION)
                        .description("Organise tabletop role-playing games: adventures, characters, notes, planning."))
                .servers(List.of(new Server().url(contextPath.isEmpty() ? "/" : contextPath)))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("The access token from POST /accounts/login, renewed with POST /accounts/refresh."))
                        .addSchemas(PROBLEM, problemSchema()))
                // secured unless the operation says otherwise (see the customizer)
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /** Adds the error answer to every operation, declares the answers as JSON, and frees the public routes from the token. */
    @Bean
    public OpenApiCustomizer errorsAndPublicRoutes() {
        Set<String> publicPosts = Set.of(SecurityConfig.PUBLIC_POST_ROUTES);
        Set<String> publicGets = Set.of(SecurityConfig.PUBLIC_GET_ROUTES);

        return openApi -> openApi.getPaths().forEach((path, item) ->
                item.readOperationsMap().forEach((method, operation) -> {
                    operation.getResponses().values().forEach(OpenApiConfig::declareJson);
                    if (operation.getSummary() == null) {
                        operation.setSummary(summaryOf(operation.getOperationId()));
                    }
                    operation.getResponses().addApiResponse("default", problemResponse());
                    if (isPublic(method.name(), path, publicPosts, publicGets)) {
                        operation.setSecurity(List.of());
                    }
                }));
    }

    /**
     * Without {@code produces} springdoc declares every answer as any type. The API answers JSON (a file, which is
     * binary, keeps its own type): a generated client needs to know it to parse the body.
     */
    private static void declareJson(ApiResponse response) {
        Content content = response.getContent();
        MediaType any = content == null ? null : content.get("*/*");
        if (any != null && !(any.getSchema() != null && "binary".equals(any.getSchema().getFormat()))) {
            content.remove("*/*");
            content.addMediaType("application/json", any);
        }
    }

    /** "getMyProfile" becomes "Get my profile": enough for the documentation until an operation needs its own words. */
    static String summaryOf(String operationId) {
        String words = operationId.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase();
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static boolean isPublic(String method, String path, Set<String> publicPosts, Set<String> publicGets) {
        return (HttpMethod.POST.name().equals(method) && publicPosts.contains(path))
                || (HttpMethod.GET.name().equals(method) && publicGets.contains(path));
    }

    private static ApiResponse problemResponse() {
        return new ApiResponse()
                .description("The error, in the same shape whatever produced it (RFC 9457).")
                .content(new Content().addMediaType("application/problem+json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM))));
    }

    private static Schema<?> problemSchema() {
        return new ObjectSchema()
                .description("An error (RFC 9457 problem details). correlationId is the identifier to quote when reporting it.")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Not Found"))
                .addProperty("status", new IntegerSchema().example(404))
                .addProperty("detail", new StringSchema().description("What went wrong, in English."))
                .addProperty("instance", new StringSchema().description("The path of the request."))
                .addProperty("correlationId", new StringSchema().description("Same value as the X-Request-Id header."))
                .addProperty("errors", new ArraySchema()
                        .description("Only for a request that is not valid: one entry per field.")
                        .items(new ObjectSchema()
                                .addProperty("field", new StringSchema())
                                .addProperty("message", new StringSchema())));
    }

}
