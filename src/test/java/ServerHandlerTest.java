import com.samarina.server.ServerApp;
import com.samarina.server.ServerHandler;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class ServerHandlerTest {
    private TestableServerHandler handler;
    private ChannelHandlerContext mockCtx;

    @BeforeEach
    void setUp() {
        handler = new TestableServerHandler();
        mockCtx = mock(ChannelHandlerContext.class);
        ServerApp.getTopics().clear();
        ServerApp.getActiveUsers().clear();
    }

    @Test
    void testCreateDuplicateTopic() {
        handler.processCommand(mockCtx, "login -u=user");
        handler.processCommand(mockCtx, "create topic -n=testTopic");
        handler.processCommand(mockCtx, "create topic -n=testTopic");
        verify(mockCtx).writeAndFlush(contains("Раздел с таким именем уже существует"));
    }

    @Test
    void testViewNonExistentTopic() {
        handler.processCommand(mockCtx, "login -u=user");
        handler.processCommand(mockCtx, "view -t=nonexistent");
        verify(mockCtx).writeAndFlush(contains("Раздел nonexistent не найден"));
    }

    @Test
    void testViewNonExistentVote() {
        handler.processCommand(mockCtx, "login -u=user");
        handler.processCommand(mockCtx, "create topic -n=testTopic");
        handler.processCommand(mockCtx, "view -t=testTopic -v=nonexistent");
        verify(mockCtx).writeAndFlush(contains("Голосование nonexistent не найдено"));
    }

    @Test
    void testVoteWithoutLogin() {
        handler.processCommand(mockCtx, "vote -t=testTopic -v=testVote");
        verify(mockCtx).writeAndFlush(contains("Для выполнения команды vote необходимо авторизоваться"));
    }

    @Test
    void testVoteInNonExistentTopic() {
        handler.processCommand(mockCtx, "login -u=user");
        handler.processCommand(mockCtx, "vote -t=nonexistent -v=testVote");
        verify(mockCtx).writeAndFlush(contains("Раздел nonexistent не найден"));
    }

    @Test
    void testVoteInNonExistentVote() {
        handler.processCommand(mockCtx, "login -u=user");
        handler.processCommand(mockCtx, "create topic -n=testTopic");
        handler.processCommand(mockCtx, "vote -t=testTopic -v=nonexistent");
        verify(mockCtx).writeAndFlush(contains("Голосование nonexistent не найдено"));
    }

    @Test
    void testDoubleVoting() {
        // Создаем голосование
        handler.processCommand(mockCtx, "login -u=user");
        handler.processCommand(mockCtx, "create topic -n=testTopic");
        handler.processCommand(mockCtx, "create vote -t=testTopic");
        handler.processCommand(mockCtx, "testVote");
        handler.processCommand(mockCtx, "question?");
        handler.processCommand(mockCtx, "2");
        handler.processCommand(mockCtx, "option1");
        handler.processCommand(mockCtx, "option2");

        // Голосуем первый раз
        handler.processCommand(mockCtx, "vote -t=testTopic -v=testVote");
        handler.processCommand(mockCtx, "1");

        // Пытаемся проголосовать второй раз
        handler.processCommand(mockCtx, "vote -t=testTopic -v=testVote");
        verify(mockCtx).writeAndFlush(contains("Вы уже голосовали в этом голосовании"));
    }

    @Test
    void testDeleteNonExistentVote() {
        handler.processCommand(mockCtx, "login -u=user");
        handler.processCommand(mockCtx, "create topic -n=testTopic");
        handler.processCommand(mockCtx, "delete -t=testTopic -v=nonexistent");
        verify(mockCtx).writeAndFlush(contains("Голосования nonexistent не существует"));
    }

    @Test
    void testLoadNonExistentFile() {
        handler.processCommand(mockCtx, "load nonexistent.json");
        verify(mockCtx).writeAndFlush(contains("Ошибка загрузки из файла"));
    }

    @Test
    void testHandleLogin() {
        handler.processCommand(mockCtx, "login -u=user");
        verify(mockCtx, times(1)).writeAndFlush(contains("Вы вошли в систему. Login: user"));
    }

    @Test
    void testLoginWithoutUsername() {
        handler.processCommand(mockCtx, "login");
        verify(mockCtx).writeAndFlush(contains("Неправильно введена команда login -u=username"));
    }

    @Test
    void testHandleCreateTopic() {
        handler.processCommand(mockCtx, "login -u=user");
        handler.processCommand(mockCtx, "create topic -t=testTopic");
        verify(mockCtx, times(1)).writeAndFlush(contains("Создан новый раздел testTopic"));
    }

    @Test
    void testLoginWithEmptyUsername() {
        handler.processCommand(mockCtx, "login -u=");
        verify(mockCtx).writeAndFlush(contains("Ошибка ввода имени пользователя username"));
    }

    @Test
    void testCreateTopicWithoutLogin() {
        handler.processCommand(mockCtx, "create topic -n=testTopic");
        verify(mockCtx).writeAndFlush(contains("Для выполнения команды create необходимо авторизоваться"));
    }

    @Test
    void testHandleViewTopics() {
        handler.processCommand(mockCtx, "login -u=user");
        handler.processCommand(mockCtx, "create topic -t=testTopic");
        handler.processCommand(mockCtx, "view");
        verify(mockCtx, times(1)).writeAndFlush(contains("Текущий список разделов"));
    }

    @Test
    void testHandleVote() {
        handler.processCommand(mockCtx, "login -u=user");
        handler.processCommand(mockCtx, "create topic -t=testTopic");
        handler.processCommand(mockCtx, "create vote -t=testTopic");
        handler.processCommand(mockCtx, "testVote");
        handler.processCommand(mockCtx, "question?");
        handler.processCommand(mockCtx, "2");
        handler.processCommand(mockCtx, "answer1");
        handler.processCommand(mockCtx, "answer2");
        handler.processCommand(mockCtx, "vote -t=testTopic -v=testVote");
        verify(mockCtx, atLeastOnce()).writeAndFlush(contains("Вы перешли к голосованию"));
    }

    @Test
    void testHandleDeleteVote() {
        handler.processCommand(mockCtx, "login -u=user");
        handler.processCommand(mockCtx, "create topic -t=testTopic");
        handler.processCommand(mockCtx, "create vote -t=testTopic");
        handler.processCommand(mockCtx, "testVote");
        handler.processCommand(mockCtx, "testQuestion");
        handler.processCommand(mockCtx, "3");
        handler.processCommand(mockCtx, "answer1");
        handler.processCommand(mockCtx, "answer2");
        handler.processCommand(mockCtx, "answer3");
        handler.processCommand(mockCtx, "delete -t=testTopic -v=testVote");
        verify(mockCtx, atLeastOnce()).writeAndFlush(contains("Голосование testVote удалено"));
    }

    @Test
    void testHandleSaveAndLoad() {
        handler.processCommand(mockCtx, "login -u=user");
        handler.processCommand(mockCtx, "create topic -t=testTopic");
        handler.processCommand(mockCtx, "create vote -t=testTopic");
        handler.processCommand(mockCtx, "testVote");
        handler.processCommand(mockCtx, "testQuestion");
        handler.processCommand(mockCtx, "3");
        handler.processCommand(mockCtx, "answer1");
        handler.processCommand(mockCtx, "answer2");
        handler.processCommand(mockCtx, "answer3");
        handler.processCommand(mockCtx, "save testdata.json");
        verify(mockCtx, atLeastOnce()).writeAndFlush(contains("Данные успешно сохранены"));
        handler.processCommand(mockCtx, "load testdata.json");
        verify(mockCtx, atLeastOnce()).writeAndFlush(contains("Данные успешно загружены"));
    }

    @Test
    void testHandleInvalidCommand() {
        handler.processCommand(mockCtx, "nocommand");
        verify(mockCtx, times(1)).writeAndFlush(contains("Неизвестная команда. Введите 'help' для вывода списка команд"));
    }

    private static class TestableServerHandler extends ServerHandler { // получаем обработчик команд
        void processCommand(ChannelHandlerContext ctx, String msg) {
            channelRead0(ctx, msg);
        }
    }
}