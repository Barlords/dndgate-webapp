package fr.barlords.dndgateapi.bootstrap.config;

import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Puts the identifier of the request ({@link CorrelationIdFilter}) in every problem details answer, so that
 * someone who reports an error can quote it and the matching log lines are found at once. Errors written by a
 * filter or by security get it from {@link ProblemJson}.
 */
@RestControllerAdvice
public class CorrelationIdProblemAdvice implements ResponseBodyAdvice<Object> {

    static final String PROPERTY = "correlationId";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public @Nullable Object beforeBodyWrite(@Nullable Object body, MethodParameter returnType, MediaType selectedContentType,
                                            Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                            ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ProblemDetail problem) {
            String id = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (id != null) {
                problem.setProperty(PROPERTY, id);
            }
        }
        return body;
    }

}
