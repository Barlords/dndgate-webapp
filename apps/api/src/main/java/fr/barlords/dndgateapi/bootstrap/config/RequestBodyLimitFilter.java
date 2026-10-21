package fr.barlords.dndgateapi.bootstrap.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Nothing limits the size of a JSON body by default: a few hundred megabytes sent to any route would be read in memory.
 * The files are limited separately (spring.servlet.multipart.*).
 * <p>
 * A body announced as too large is refused at once. A body of unknown length (chunked) is read, up to the limit,
 * before the request goes on.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public RequestBodyLimitFilter(@Value("${app.limits.max-body-bytes:524288}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long announced = request.getContentLengthLong();

        if (announced > maxBytes) {
            tooLarge(response);
            return;
        }

        if (announced < 0 && request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null) {
            byte[] body = request.getInputStream().readNBytes((int) maxBytes + 1);
            if (body.length > maxBytes) {
                tooLarge(response);
                return;
            }
            chain.doFilter(new BufferedRequest(request, body), response);
            return;
        }

        chain.doFilter(request, response);
    }

    private void tooLarge(HttpServletResponse response) throws IOException {
        ProblemJson.write(response, HttpStatus.CONTENT_TOO_LARGE, "The request is too large");
    }

    private static class BufferedRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        BufferedRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream delegate = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return delegate.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return delegate.read(buffer, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return delegate.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
