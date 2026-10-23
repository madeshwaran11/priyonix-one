package backend;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    static final Map<String, String> CORE = Map.ofEntries(Map.entry("employees", "employees"),
            Map.entry("projects", "projects"), Map.entry("tasks", "tasks"), Map.entry("clients", "clients"),
            Map.entry("leads", "leads"), Map.entry("invoices", "invoices"), Map.entry("payments", "payments"),
            Map.entry("expenses", "expenses"), Map.entry("campaigns", "campaigns"), Map.entry("follow-ups", "followups"));
    private static final java.util.Set<String> ADMIN_MODULES = java.util.Set.of("employees", "invoices", "payments", "expenses");
    private final MongoTemplate mongo;
    private final GridFsTemplate files;
    private final WorkspaceService workspace;
    public AttachmentService(MongoTemplate mongo, GridFsTemplate files, WorkspaceService workspace) {
        this.mongo = mongo; this.files = files; this.workspace = workspace;
    }
    public record Metadata(String id, String module, String recordId, String filename, String contentType,
            long size, String createdBy, Instant createdAt) {}
    public record Download(Metadata metadata, byte[] bytes) {}

    public List<Metadata> list(String module, String recordId, Authentication auth) {
        authorize(module, recordId, auth);
        Query q = Query.query(Criteria.where("module").is(module).and("recordId").is(recordId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongo.find(q, Document.class, "platform_attachments").stream().map(AttachmentService::metadata).toList();
    }

    public Metadata upload(String module, String recordId, MultipartFile file, Authentication auth) {
        authorize(module, recordId, auth);
        if (file == null || file.isEmpty()) throw new InvalidRequestException("Choose a non-empty file");
        if (file.getSize() > MAX_BYTES) throw new InvalidRequestException("Attachments must be 5 MB or smaller");
        String filename = safeFilename(file.getOriginalFilename());
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (IOException ex) { throw new InvalidRequestException("Unable to read the uploaded file"); }
        if (bytes.length > MAX_BYTES) throw new InvalidRequestException("Attachments must be 5 MB or smaller");
        String type = validateContent(filename, bytes);
        String id = UUID.randomUUID().toString();
        ObjectId blob = files.store(new java.io.ByteArrayInputStream(bytes), filename, type, new Document("attachmentId", id));
        Document doc = new Document("_id", id).append("module", module).append("recordId", recordId)
                .append("filename", filename).append("contentType", type).append("size", (long) bytes.length)
                .append("createdBy", auth.getName()).append("createdAt", Date.from(Instant.now())).append("blobId", blob.toHexString());
        try { mongo.insert(doc, "platform_attachments"); }
        catch (RuntimeException ex) { files.delete(Query.query(Criteria.where("_id").is(blob))); throw ex; }
        return metadata(doc);
    }

    public Download download(String id, Authentication auth) {
        Document doc = attachment(id);
        authorize(doc.getString("module"), doc.getString("recordId"), auth);
        var file = files.findOne(Query.query(Criteria.where("_id").is(new ObjectId(doc.getString("blobId")))));
        if (file == null) throw new ResourceNotFoundException("Attachment content not found");
        try (var stream = files.getResource(file).getInputStream()) {
            return new Download(metadata(doc), stream.readNBytes((int) MAX_BYTES + 1));
        } catch (IOException ex) { throw new InvalidRequestException("Unable to read attachment content"); }
    }

    public void delete(String id, Authentication auth) {
        Document doc = attachment(id);
        authorize(doc.getString("module"), doc.getString("recordId"), auth);
        if (!PlatformService.isAdmin(auth) && !auth.getName().equalsIgnoreCase(doc.getString("createdBy")))
            throw new AccessDeniedException("Only the uploader or an administrator may remove an attachment");
        files.delete(Query.query(Criteria.where("_id").is(new ObjectId(doc.getString("blobId")))));
        mongo.remove(Query.query(Criteria.where("_id").is(id)), "platform_attachments");
    }

    void authorize(String module, String recordId, Authentication auth) {
        PlatformService.required(module, "Module", 80); PlatformService.required(recordId, "Record", 100);
        if (CORE.containsKey(module)) {
            if (ADMIN_MODULES.contains(module)) PlatformService.requireAdmin(auth);
            if (!mongo.exists(idQuery(recordId), CORE.get(module))) throw new ResourceNotFoundException("Linked record not found");
        } else { workspace.get(module, recordId, auth); }
    }

    static Query idQuery(String id) {
        return Query.query(ObjectId.isValid(id)
                ? Criteria.where("_id").in(id, new ObjectId(id)) : Criteria.where("_id").is(id));
    }

    private Document attachment(String id) {
        Document doc = mongo.findById(id, Document.class, "platform_attachments");
        if (doc == null) throw new ResourceNotFoundException("Attachment not found");
        return doc;
    }

    static String safeFilename(String original) {
        String name = original == null ? "attachment" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^a-zA-Z0-9._ -]", "_");
        if (name.length() > 180 || name.isBlank() || name.startsWith(".")) throw new InvalidRequestException("Choose a valid filename up to 180 characters");
        return name;
    }

    static String validateContent(String filename, byte[] bytes) {
        String name = filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf") && starts(bytes, new byte[]{'%', 'P', 'D', 'F', '-'})) return "application/pdf";
        if (name.endsWith(".png") && starts(bytes, new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10})) return "image/png";
        if ((name.endsWith(".jpg") || name.endsWith(".jpeg")) && starts(bytes, new byte[]{(byte)255, (byte)216, (byte)255})) return "image/jpeg";
        if (name.endsWith(".txt") || name.endsWith(".csv")) {
            for (byte b : bytes) if (b == 0) throw new InvalidRequestException("Text files must contain plain UTF-8 text");
            if (starts(bytes, new byte[]{'M','Z'}) || starts(bytes, new byte[]{127,'E','L','F'}))
                throw new InvalidRequestException("Executable files are not allowed");
            try { StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)); }
            catch (CharacterCodingException ex) { throw new InvalidRequestException("Text files must use UTF-8 encoding"); }
            return name.endsWith(".csv") ? "text/csv" : "text/plain";
        }
        throw new InvalidRequestException("Allowed attachments: PDF, PNG, JPG, TXT and CSV with matching file content");
    }

    private static boolean starts(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }

    private static Metadata metadata(Document d) {
        return new Metadata(d.getString("_id"), d.getString("module"), d.getString("recordId"),
                d.getString("filename"), d.getString("contentType"), ((Number)d.get("size")).longValue(),
                d.getString("createdBy"), d.getDate("createdAt").toInstant());
    }
}
