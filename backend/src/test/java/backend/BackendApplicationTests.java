package backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that the full application context starts.
 *
 * Dummy properties are supplied so the test never depends on (or touches)
 * the real MongoDB Atlas cluster or real credentials, even when the
 * developer shell has MONGODB_URI / JWT_SECRET / ADMIN_* exported.
 */
@SpringBootTest(properties = {
		"spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
		"JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
		"ADMIN_EMAIL=",
		"ADMIN_PASSWORD="
})
class BackendApplicationTests extends PlatformMockDatabaseTestSupport {

	@Test
	void contextLoads() {
	}

}
