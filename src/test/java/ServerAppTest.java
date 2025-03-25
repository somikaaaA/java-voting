import com.samarina.model.Topic;
import com.samarina.server.ServerApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ServerAppTest {
    private static final String TEST_FILE = "test_data.json";

    @BeforeEach
    @AfterEach
    void cleanup() throws Exception {
        // Удаляем тестовый файл перед и после каждого теста
        Path file = Path.of("data", TEST_FILE);
        if (Files.exists(file)) {
            Files.delete(file);
        }
        ServerApp.getTopics().clear();
        ServerApp.getActiveUsers().clear();
    }

    @Test
    void shouldLoginAndLogoutUser() {
        assertTrue(ServerApp.loginNewUser("user1"));
        assertFalse(ServerApp.loginNewUser("user1")); // Повторный логин
        assertEquals(1, ServerApp.getActiveUsers().size());

        ServerApp.logoutUser("user1");
        assertEquals(0, ServerApp.getActiveUsers().size());
    }

    @Test
    void shouldSaveAndLoadData() {
        // Подготовка тестовых данных
        Topic topic = new Topic("Test");
        ServerApp.getTopics().put("Test", topic);
        ServerApp.loginNewUser("user1");

        // Сохраняем
        ServerApp.save(TEST_FILE);

        // Загружаем
        ServerApp.load(TEST_FILE);

        // Проверяем
        assertEquals(1, ServerApp.getTopics().size());
        assertTrue(ServerApp.getTopics().containsKey("Test"));
        assertEquals(1, ServerApp.getActiveUsers().size());
    }
}
