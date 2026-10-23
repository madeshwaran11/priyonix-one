package backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

class AttachmentServiceTest {
    @Test void filenamesAreSanitizedAndCannotEscapeStorage() {
        assertEquals("invoice.pdf", AttachmentService.safeFilename("C:\\secrets\\invoice.pdf"));
        assertEquals("invoice.pdf", AttachmentService.safeFilename("../../invoice.pdf"));
        assertThrows(InvalidRequestException.class, () -> AttachmentService.safeFilename(".env"));
    }
    @Test void disguisedExecutableAndMismatchedMagicAreRejected() {
        assertThrows(InvalidRequestException.class, () -> AttachmentService.validateContent("report.pdf", "MZpayload".getBytes()));
        assertThrows(InvalidRequestException.class, () -> AttachmentService.validateContent("report.txt", "MZpayload".getBytes()));
        assertThrows(InvalidRequestException.class, () -> AttachmentService.validateContent("image.exe", new byte[]{1,2,3}));
        assertEquals("application/pdf", AttachmentService.validateContent("report.pdf", "%PDF-1.7".getBytes()));
        assertEquals("text/csv", AttachmentService.validateContent("report.csv", "Name,Amount\nTest,100".getBytes(StandardCharsets.UTF_8)));
    }
    @Test void employeeCannotAttachToSensitiveManagementRecords() {
        MongoTemplate mongo = mock(MongoTemplate.class); GridFsTemplate grid = mock(GridFsTemplate.class);
        AttachmentService service = new AttachmentService(mongo, grid, mock(WorkspaceService.class));
        assertThrows(AccessDeniedException.class, () -> service.upload("invoices", "id", new MockMultipartFile("file", "file.txt", "text/plain", "Text".getBytes()),
                PlatformServiceTest.actor("employee@example.test", "EMPLOYEE")));
        verifyNoInteractions(mongo, grid);
    }
}
