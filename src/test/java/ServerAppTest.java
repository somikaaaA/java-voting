import com.samarina.model.Topic;
import com.samarina.server.ServerApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServerAppTest {

    @BeforeEach
    void setUp() {
        ServerApp.getTopics().clear();
        ServerApp.getActiveUsers().clear();
    }

    @Test
    void testLoginUser() {
        assertTrue(ServerApp.loginUser("user"));
        assertFalse(ServerApp.loginUser("user"));
    }

    @Test
    void testLogoutUser() {
        ServerApp.loginUser("user");
        assertEquals(1, ServerApp.getActiveUsers().size());
        ServerApp.logoutUser("user");
        assertEquals(0, ServerApp.getActiveUsers().size());
    }

    @Test
    void testCreateAndRetrieveTopic() {
        Map<String, Topic> topics = ServerApp.getTopics();
        assertTrue(topics.isEmpty());
        topics.put("testTopic", new Topic("testTopic"));
        assertEquals(1, topics.size());
        assertTrue(topics.containsKey("testTopic"));
    }

    @Test
    void testSaveAndLoad() {
        String filename = "test.json";
        ServerApp.getTopics().put("testTopic", new Topic("testTopic"));
        ServerApp.save(filename);

        ServerApp.getTopics().clear();
        assertTrue(ServerApp.getTopics().isEmpty());

        ServerApp.load(filename);
        assertFalse(ServerApp.getTopics().isEmpty());
        assertTrue(ServerApp.getTopics().containsKey("testTopic"));
    }
}