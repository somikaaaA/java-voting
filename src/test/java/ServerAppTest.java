import com.samarina.model.Topic;
import com.samarina.server.ServerApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ServerAppTest {

    @BeforeEach
    void setUp() {
        ServerApp.getTopics().clear();
        ServerApp.getActiveUsers().clear();
    }

    @AfterEach
    void tearDown() {
        ServerApp.getTopics().clear();
        ServerApp.getActiveUsers().clear();
    }

    @Test
    void testLoginNewUser() {
        assertTrue(ServerApp.loginNewUser("user1"));
        assertTrue(ServerApp.getActiveUsers().contains("user1"));
        assertEquals(1, ServerApp.getActiveUsers().size());
    }

    @Test
    void testLoginExistingUser() {
        ServerApp.loginNewUser("user1");
        assertFalse(ServerApp.loginNewUser("user1"));
        assertEquals(1, ServerApp.getActiveUsers().size());
    }

    @Test
    void testLogoutUser() {
        ServerApp.loginNewUser("user1");
        ServerApp.logoutUser("user1");
        assertFalse(ServerApp.getActiveUsers().contains("user1"));
        assertEquals(0, ServerApp.getActiveUsers().size());
    }

    @Test
    void testLogoutNonExistingUser() {
        // Не должно быть исключения
        ServerApp.logoutUser("nonexistent");
    }

//    @Test
//    void testSaveAndLoad() {
//        // Подготовка тестовых данных
//        Topic topic = new Topic("TestTopic");
//        ServerApp.getTopics().put("TestTopic", topic);
//        ServerApp.loginNewUser("user1");
//
//        String filename = "test_save.json";
//
//        // Сохранение
//        ServerApp.save(filename);
//
//        // Очистка
//        ServerApp.getTopics().clear();
//        ServerApp.getActiveUsers().clear();
//
//        // Загрузка
//        ServerApp.load(filename);
//
//        // Проверки
//        assertEquals(1, ServerApp.getTopics().size());
//        assertNotNull(ServerApp.getTopics().get("TestTopic"));
//        // Активные пользователи не сохраняются/не загружаются
//        assertEquals(0, ServerApp.getActiveUsers().size());
//
//        // Удаление тестового файла
//        new File("data/" + filename).delete();
//    }
}
