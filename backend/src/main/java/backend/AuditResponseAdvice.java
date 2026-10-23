package backend;

import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Carries only the created object's identifier to the audit interceptor. */
@ControllerAdvice
public class AuditResponseAdvice implements ResponseBodyAdvice<Object> {
    @Override public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public Object beforeBodyWrite(Object body, MethodParameter type, MediaType media,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (request instanceof ServletServerHttpRequest servlet && request.getMethod().name().equals("POST")) {
            String id = identifier(body);
            if (id != null) servlet.getServletRequest().setAttribute("audit.resourceId", id);
        }
        return body;
    }
    private String identifier(Object body) {
        if (body == null) return null;
        if (body instanceof Map<?, ?> map) {
            Object id = map.get("id");
            if (id instanceof String s) return s;
            for (String key : new String[]{"employee", "project", "task", "client", "lead", "invoice", "payment", "expense", "campaign", "followUp", "record"}) {
                if (map.containsKey(key)) return identifier(map.get(key));
            }
            return null;
        }
        // Domain DTOs use either record-style id() or bean-style getId(). Do not inspect other properties.
        if (!body.getClass().getPackageName().equals("backend")) return null;
        for (String getter : new String[]{"id", "getId"}) {
            try {
                Object id = body.getClass().getMethod(getter).invoke(body);
                if (id instanceof String s) return s;
            } catch (ReflectiveOperationException ignored) { }
        }
        return null;
    }
}
