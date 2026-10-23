package backend;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/platform/attachments")
public class AttachmentController {
    private final AttachmentService service;
    public AttachmentController(AttachmentService service) { this.service = service; }
    @GetMapping
    public List<AttachmentService.Metadata> list(@RequestParam String module, @RequestParam String recordId, Authentication auth) {
        return service.list(module, recordId, auth);
    }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public AttachmentService.Metadata upload(@RequestParam String module, @RequestParam String recordId,
            @RequestPart("file") MultipartFile file, Authentication auth) { return service.upload(module, recordId, file, auth); }
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id, Authentication auth) {
        AttachmentService.Download result = service.download(id, auth);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.metadata().contentType()))
                .contentLength(result.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(result.metadata().filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(result.bytes());
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, Authentication auth) { service.delete(id, auth); }
}
