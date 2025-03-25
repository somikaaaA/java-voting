import com.samarina.server.ServerApp;
import com.samarina.server.ServerHandler;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ServerHandlerTest {
    private ServerHandler serverHandler;

    @Mock
    private ChannelHandlerContext ctx;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        serverHandler = new ServerHandler();
        ServerApp.getTopics().clear();
        ServerApp.getActiveUsers().clear();
    }

    @Test
    void shouldHandleLoginCommand() {
        serverHandler.channelRead0(ctx, "login -u=testuser");
        verify(ctx).writeAndFlush("Вы вошли под пользователем testuser\n");
        assertTrue(ServerApp.getActiveUsers().contains("testuser"));
    }

    @Test
    void shouldHandleCreateTopicCommand() {
        // Сначала логинимся
        serverHandler.channelRead0(ctx, "login -u=admin");
        // Затем создаем тему
        serverHandler.channelRead0(ctx, "create topic -n=Programming");
        verify(ctx).writeAndFlush("Создан раздел голосования: Programming\n");
        assertTrue(ServerApp.getTopics().containsKey("Programming"));
    }

    @Test
    void shouldHandleHelpCommand() {
        serverHandler.channelRead0(ctx, "help");
        verify(ctx).writeAndFlush(contains("Доступные команды:"));
    }
}
