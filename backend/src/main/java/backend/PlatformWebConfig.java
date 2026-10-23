package backend;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PlatformWebConfig implements WebMvcConfigurer {
    private final AuditInterceptor audit;
    public PlatformWebConfig(AuditInterceptor audit) { this.audit = audit; }
    @org.springframework.context.annotation.Bean
    public jakarta.servlet.MultipartConfigElement multipartConfigElement() {
        return new jakarta.servlet.MultipartConfigElement("", AttachmentService.MAX_BYTES,
                AttachmentService.MAX_BYTES + 1024 * 1024, 0);
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(audit).addPathPatterns("/api/**");
    }
}
