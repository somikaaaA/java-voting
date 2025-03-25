import com.samarina.model.Topic;
import com.samarina.model.Vote;
import com.samarina.server.ServerApp;
import com.samarina.server.ServerHandler;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerHandlerTest {
    @Mock
    private ChannelHandlerContext ctx;

    private ServerHandler serverHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        serverHandler = new ServerHandler();
        ServerApp.getTopics().clear();
        ServerApp.getActiveUsers().clear();
    }

    @Test
    void testHandleLoginSuccess() {
        serverHandler.channelRead0(ctx, "login -u=user1");
        verify(ctx).writeAndFlush("Добро пожаловать, user1!\n");
        assertTrue(ServerApp.getActiveUsers().contains("user1"));
    }

    @Test
    void testHandleLoginDuplicate() {
        ServerApp.loginNewUser("user1");
        serverHandler.channelRead0(ctx, "login -u=user1");
        verify(ctx).writeAndFlush("Имя user1 уже используется\n");
    }

//    @Test
//    void testHandleCreateTopic() {
//        ServerApp.loginNewUser("user1");
//        serverHandler.channelRead0(ctx, "create topic -n=TestTopic");
//
//        verify(ctx).writeAndFlush("Новый раздел создан: TestTopic\n");
//        assertTrue(ServerApp.getTopics().containsKey("TestTopic"));
//    }
//
//    @Test
//    void testHandleCreateVote() {
//        // Подготовка
//        ServerApp.loginNewUser("user1");
//        ServerApp.getTopics().put("TestTopic", new Topic("TestTopic"));
//
//        // Последовательность команд для создания голосования
//        serverHandler.channelRead0(ctx, "create vote -t=TestTopic");
//        verify(ctx).writeAndFlush("Создаем голосование в разделе TestTopic\nУкажите название:");
//
//        serverHandler.channelRead0(ctx, "NewVote");
//        verify(ctx).writeAndFlush("Опишите суть голосования\n");
//
//        serverHandler.channelRead0(ctx, "Vote description");
//        verify(ctx).writeAndFlush("Сколько будет вариантов ответа?\n");
//
//        serverHandler.channelRead0(ctx, "2");
//        verify(ctx).writeAndFlush("Введите первый вариант\n");
//
//        serverHandler.channelRead0(ctx, "Option1");
//        verify(ctx).writeAndFlush("Введите вариант 2\n");
//
//        serverHandler.channelRead0(ctx, "Option2");
//        verify(ctx).writeAndFlush("Голосование создано успешно\n");
//
//        // Проверка результата
//        Topic topic = ServerApp.getTopics().get("TestTopic");
//        Vote vote = topic.getVote("NewVote");
//        assertNotNull(vote);
//        assertEquals("Vote description", vote.getDescription());
//        assertEquals(2, vote.getOptions().size());
//    }
}
