package com.samarina.server;

import com.samarina.model.Topic;
import com.samarina.model.User;
import com.samarina.model.Vote;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Обработчик команд сервера для работы с клиентами
 */
public class ServerHandler extends SimpleChannelInboundHandler<String> {
    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);

    // Состояния клиента для обработки многошаговых команд
    private enum State {
        MENU,                   // Основное меню
        CREATING_VOTE_NAME,      // Ввод названия голосования
        CREATING_VOTE_DESC,      // Ввод описания голосования
        CREATING_VOTE_OPTIONS_COUNT, // Ввод количества вариантов
        CREATING_VOTE_OPTIONS,   // Ввод вариантов ответов
        VOTING                  // Процесс голосования
    }

    /**
     * Класс для хранения состояния клиента
     */
    private static class ClientState {
        User user;                  // Текущий пользователь
        State state = State.MENU;   // Текущее состояние
        String currentTopic;        // Текущий раздел (для создания голосования)
        String voteName;            // Название голосования
        String voteDescription;     // Описание голосования
        List<String> voteOptions = new ArrayList<>(); // Варианты ответов
        int optionsCount;           // Количество вариантов ответов
        List<String> votingOptions; // Варианты для голосования
    }

    // Хранилище состояний клиентов
    private final Map<ChannelHandlerContext, ClientState> clientStates = new HashMap<>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        // Получаем или создаем состояние для текущего клиента
        ClientState state = clientStates.computeIfAbsent(ctx, k -> new ClientState());

        try {
            // Обработка сообщения в зависимости от состояния
            switch (state.state) {
                case MENU:
                    handleCommand(ctx, msg, state);
                    break;
                case CREATING_VOTE_NAME:
                    handleVoteName(ctx, msg, state);
                    break;
                case CREATING_VOTE_DESC:
                    handleVoteDescription(ctx, msg, state);
                    break;
                case CREATING_VOTE_OPTIONS_COUNT:
                    handleOptionsCount(ctx, msg, state);
                    break;
                case CREATING_VOTE_OPTIONS:
                    handleVoteOption(ctx, msg, state);
                    break;
                case VOTING:
                    handleVoteSelection(ctx, msg, state);
                    break;
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке сообщения от клиента: {}", e.getMessage(), e);
            ctx.writeAndFlush("Ошибка: " + e.getMessage() + "\n");
        }
    }

    /**
     * Обработка основной команды от клиента
     */
    private void handleCommand(ChannelHandlerContext ctx, String msg, ClientState state) {
        String[] parts = msg.trim().split("\\s+");
        String command = parts[0].toLowerCase();

        switch (command) {
            case "login":
                handleLogin(ctx, parts, state);
                break;
            case "create":
                handleCreate(ctx, parts, state);
                break;
            case "view":
                handleView(ctx, parts, state);
                break;
            case "vote":
                handleVote(ctx, parts, state);
                break;
            case "delete":
                handleDelete(ctx, parts, state);
                break;
            case "save":
                handleSave(ctx, parts);
                break;
            case "load":
                handleLoad(ctx, parts);
                break;
            case "exit":
                handleExit(ctx, state);
                break;
            default:
                ctx.writeAndFlush("Неизвестная команда\n");
                logger.warn("Получена неизвестная команда: {}", msg);
                break;
        }
    }

    /**
     * Обработка команды login
     */
    private void handleLogin(ChannelHandlerContext ctx, String[] parts, ClientState state) {
        if (parts.length < 2 || !parts[1].startsWith("-u=")) {
            ctx.writeAndFlush("Использование: login -u=имя_пользователя\n");
            return;
        }

        String username = parts[1].substring(3);
        if (username.isEmpty()) {
            ctx.writeAndFlush("Имя пользователя не может быть пустым\n");
            return;
        }

        User user = new User(username);

        if (ServerApp.loginUser(user)) {
            state.user = user;
            ctx.writeAndFlush("Вы авторизованы как " + username + "\n");
            logger.info("Пользователь {} успешно авторизован с адреса {}",
                    username, ctx.channel().remoteAddress());
        } else {
            ctx.writeAndFlush("Пользователь " + username + " уже авторизован\n");
            logger.warn("Попытка повторной авторизации пользователя: {}", username);
        }
    }

    /**
     * Обработка команды create
     */
    private void handleCreate(ChannelHandlerContext ctx, String[] parts, ClientState state) {
        if (state.user == null) {
            ctx.writeAndFlush("Сначала выполните авторизацию (команда login)\n");
            return;
        }

        if (parts.length < 2) {
            ctx.writeAndFlush("Использование: create topic -n=имя ИЛИ create vote -t=раздел\n");
            return;
        }

        String subCommand = parts[1].toLowerCase();
        switch (subCommand) {
            case "topic":
                createTopic(ctx, parts, state);
                break;
            case "vote":
                createVote(ctx, parts, state);
                break;
            default:
                ctx.writeAndFlush("Неизвестная подкоманда create\n");
                break;
        }
    }

    /**
     * Создание нового раздела
     */
    private void createTopic(ChannelHandlerContext ctx, String[] parts, ClientState state) {
        if (parts.length < 3 || !parts[2].startsWith("-n=")) {
            ctx.writeAndFlush("Использование: create topic -n=имя_раздела\n");
            return;
        }

        String topicName = parts[2].substring(3);
        synchronized (ServerApp.getTopics()) {
            if (ServerApp.getTopics().containsKey(topicName)) {
                ctx.writeAndFlush("Раздел " + topicName + " уже существует\n");
                logger.warn("Попытка создания существующего раздела: {}", topicName);
            } else {
                ServerApp.getTopics().put(topicName, new Topic(topicName));
                ctx.writeAndFlush("Раздел " + topicName + " успешно создан\n");
                logger.info("Создан новый раздел {} пользователем {}",
                        topicName, state.user.getName());
            }
        }
    }

    /**
     * Начало создания голосования
     */
    private void createVote(ChannelHandlerContext ctx, String[] parts, ClientState state) {
        if (parts.length < 3 || !parts[2].startsWith("-t=")) {
            ctx.writeAndFlush("Использование: create vote -t=раздел\n");
            return;
        }

        String topicName = parts[2].substring(3);
        synchronized (ServerApp.getTopics()) {
            if (!ServerApp.getTopics().containsKey(topicName)) {
                ctx.writeAndFlush("Раздел " + topicName + " не найден\n");
                logger.warn("Попытка создания голосования в несуществующем разделе: {}", topicName);
                return;
            }

            state.currentTopic = topicName;
            state.state = State.CREATING_VOTE_NAME;
            ctx.writeAndFlush("Введите название голосования:\n");
            logger.info("Пользователь {} начал создание голосования в разделе {}",
                    state.user.getName(), topicName);
        }
    }

    /**
     * Обработка названия голосования
     */
    private void handleVoteName(ChannelHandlerContext ctx, String msg, ClientState state) {
        synchronized (ServerApp.getTopics()) {
            Topic topic = ServerApp.getTopics().get(state.currentTopic);
            if (topic.getVote(msg) != null) {
                ctx.writeAndFlush("Голосование с таким названием уже существует. Введите другое название:\n");
                return;
            }

            state.voteName = msg;
            state.state = State.CREATING_VOTE_DESC;
            ctx.writeAndFlush("Введите описание голосования:\n");
        }
    }

    /**
     * Обработка описания голосования
     */
    private void handleVoteDescription(ChannelHandlerContext ctx, String msg, ClientState state) {
        state.voteDescription = msg;
        state.state = State.CREATING_VOTE_OPTIONS_COUNT;
        ctx.writeAndFlush("Введите количество вариантов ответа:\n");
    }

    /**
     * Обработка количества вариантов ответа
     */
    private void handleOptionsCount(ChannelHandlerContext ctx, String msg, ClientState state) {
        try {
            int count = Integer.parseInt(msg);
            if (count < 1) {
                ctx.writeAndFlush("Количество вариантов должно быть больше 0. Введите снова:\n");
                return;
            }

            state.optionsCount = count;
            state.state = State.CREATING_VOTE_OPTIONS;
            state.voteOptions.clear();
            ctx.writeAndFlush("Введите вариант ответа 1:\n");
        } catch (NumberFormatException e) {
            ctx.writeAndFlush("Некорректное число. Введите количество вариантов ответа:\n");
        }
    }

    /**
     * Обработка вариантов ответа
     */
    private void handleVoteOption(ChannelHandlerContext ctx, String msg, ClientState state) {
        if (msg.trim().isEmpty()) {
            ctx.writeAndFlush("Вариант ответа не может быть пустым. Введите снова:\n");
            return;
        }

        state.voteOptions.add(msg);

        if (state.voteOptions.size() < state.optionsCount) {
            ctx.writeAndFlush("Введите вариант ответа " + (state.voteOptions.size() + 1) + ":\n");
        } else {
            // Все варианты введены, создаем голосование
            synchronized (ServerApp.getTopics()) {
                Topic topic = ServerApp.getTopics().get(state.currentTopic);
                Vote vote = new Vote(state.voteName, state.voteDescription, state.user);

                // Добавляем варианты ответов
                for (String option : state.voteOptions) {
                    vote.addOption(option);
                }

                topic.addVote(vote);
                ctx.writeAndFlush("Голосование \"" + state.voteName + "\" успешно создано в разделе \"" +
                        state.currentTopic + "\"\n");

                logger.info("Пользователь {} создал голосование {} в разделе {}",
                        state.user.getName(), state.voteName, state.currentTopic);

                // Сбрасываем состояние
                resetVoteCreationState(state);
            }
        }
    }

    /**
     * Обработка команды view
     */
    private void handleView(ChannelHandlerContext ctx, String[] parts, ClientState state) {
        if (state.user == null) {
            ctx.writeAndFlush("Сначала выполните авторизацию (команда login)\n");
            return;
        }

        String topicName = null;
        String voteName = null;

        // Парсим параметры
        for (String part : parts) {
            if (part.startsWith("-t=")) {
                topicName = part.substring(3);
            } else if (part.startsWith("-v=")) {
                voteName = part.substring(3);
            }
        }

        synchronized (ServerApp.getTopics()) {
            if (topicName == null && voteName == null) {
                // Просмотр всех разделов
                if (ServerApp.getTopics().isEmpty()) {
                    ctx.writeAndFlush("Нет созданных разделов\n");
                } else {
                    StringBuilder response = new StringBuilder("Список разделов:\n");
                    ServerApp.getTopics().forEach((name, topic) ->
                            response.append("- ").append(name)
                                    .append(" (голосований: ").append(topic.getVotesCount()).append(")\n"));
                    ctx.writeAndFlush(response.toString());
                }
            } else if (topicName != null && voteName == null) {
                // Просмотр голосований в разделе
                Topic topic = ServerApp.getTopics().get(topicName);
                if (topic == null) {
                    ctx.writeAndFlush("Раздел " + topicName + " не найден\n");
                } else {
                    List<Vote> votes = topic.getVotes();
                    if (votes.isEmpty()) {
                        ctx.writeAndFlush("В разделе " + topicName + " нет голосований\n");
                    } else {
                        StringBuilder response = new StringBuilder("Голосования в разделе " + topicName + ":\n");
                        votes.forEach(vote ->
                                response.append("- ").append(vote.getName()).append("\n"));
                        ctx.writeAndFlush(response.toString());
                    }
                }
            } else if (topicName != null && voteName != null) {
                // Просмотр конкретного голосования
                Topic topic = ServerApp.getTopics().get(topicName);
                if (topic == null) {
                    ctx.writeAndFlush("Раздел " + topicName + " не найден\n");
                } else {
                    Vote vote = topic.getVote(voteName);
                    if (vote == null) {
                        ctx.writeAndFlush("Голосование " + voteName + " не найдено в разделе " + topicName + "\n");
                    } else {
                        ctx.writeAndFlush(vote.toString());
                    }
                }
            } else {
                ctx.writeAndFlush("Некорректные параметры команды view\n");
            }
        }
    }

    /**
     * Обработка команды vote
     */
    private void handleVote(ChannelHandlerContext ctx, String[] parts, ClientState state) {
        if (state.user == null) {
            ctx.writeAndFlush("Сначала выполните авторизацию (команда login)\n");
            return;
        }

        String topicName = null;
        String voteName = null;

        // Парсим параметры
        for (String part : parts) {
            if (part.startsWith("-t=")) {
                topicName = part.substring(3);
            } else if (part.startsWith("-v=")) {
                voteName = part.substring(3);
            }
        }

        if (topicName == null || voteName == null) {
            ctx.writeAndFlush("Использование: vote -t=раздел -v=голосование\n");
            return;
        }

        synchronized (ServerApp.getTopics()) {
            Topic topic = ServerApp.getTopics().get(topicName);
            if (topic == null) {
                ctx.writeAndFlush("Раздел " + topicName + " не найден\n");
                return;
            }

            Vote vote = topic.getVote(voteName);
            if (vote == null) {
                ctx.writeAndFlush("Голосование " + voteName + " не найдено в разделе " + topicName + "\n");
                return;
            }

//            // Проверяем, не голосовал ли уже пользователь
//            if (vote.getOptions().entrySet().stream()
//                    .anyMatch(e -> e.getValue() > 0 &&
//                            vote.getVoters().stream()
//                                    .anyMatch(u -> u.getName().equals(state.user.getName())))) {
//                ctx.writeAndFlush("Вы уже голосовали в этом голосовании\n");
//                return;
//            }

            // Показываем варианты для голосования
            Map<String, Integer> options = vote.getOptions();
            StringBuilder response = new StringBuilder("Голосование: " + vote.getName() + "\n");
            response.append("Описание: ").append(vote.getDescription()).append("\n");
            response.append("Варианты ответа:\n");

            List<String> optionList = new ArrayList<>(options.keySet());
            for (int i = 0; i < optionList.size(); i++) {
                response.append(i + 1).append(". ").append(optionList.get(i)).append("\n");
            }

            response.append("Введите номер варианта:\n");
            ctx.writeAndFlush(response.toString());

            // Сохраняем состояние для обработки выбора
            state.state = State.VOTING;
            state.currentTopic = topicName;
            state.voteName = voteName;
            state.votingOptions = optionList;
        }
    }

    /**
     * Обработка выбора варианта при голосовании
     */
    private void handleVoteSelection(ChannelHandlerContext ctx, String msg, ClientState state) {
        try {
            int choice = Integer.parseInt(msg);
            if (choice < 1 || choice > state.votingOptions.size()) {
                ctx.writeAndFlush("Некорректный номер варианта. Введите снова:\n");
                return;
            }

            synchronized (ServerApp.getTopics()) {
                Topic topic = ServerApp.getTopics().get(state.currentTopic);
                Vote vote = topic.getVote(state.voteName);

                String selectedOption = state.votingOptions.get(choice - 1);
                vote.voteForOption(selectedOption);
//                vote.addVoter(state.user);

                ctx.writeAndFlush("Ваш голос за вариант \"" + selectedOption + "\" учтен\n");
                logger.info("Пользователь {} проголосовал в голосовании {} раздела {}",
                        state.user.getName(), state.voteName, state.currentTopic);

                // Сбрасываем состояние
                state.state = State.MENU;
                state.currentTopic = null;
                state.voteName = null;
                state.votingOptions = null;
            }
        } catch (NumberFormatException e) {
            ctx.writeAndFlush("Некорректный номер варианта. Введите число:\n");
        }
    }

    /**
     * Обработка команды delete
     */
    private void handleDelete(ChannelHandlerContext ctx, String[] parts, ClientState state) {
        if (state.user == null) {
            ctx.writeAndFlush("Сначала выполните авторизацию (команда login)\n");
            return;
        }

        String topicName = null;
        String voteName = null;

        // Парсим параметры
        for (String part : parts) {
            if (part.startsWith("-t=")) {
                topicName = part.substring(3);
            } else if (part.startsWith("-v=")) {
                voteName = part.substring(3);
            }
        }

        if (topicName == null || voteName == null) {
            ctx.writeAndFlush("Использование: delete -t=раздел -v=голосование\n");
            return;
        }

        synchronized (ServerApp.getTopics()) {
            Topic topic = ServerApp.getTopics().get(topicName);
            if (topic == null) {
                ctx.writeAndFlush("Раздел " + topicName + " не найден\n");
                return;
            }

            Vote vote = topic.getVote(voteName);
            if (vote == null) {
                ctx.writeAndFlush("Голосование " + voteName + " не найдено в разделе " + topicName + "\n");
                return;
            }

            // Проверяем, является ли пользователь создателем
            if (!vote.isCreator(state.user)) {
                ctx.writeAndFlush("Вы можете удалять только созданные вами голосования\n");
                logger.warn("Пользователь {} попытался удалить чужое голосование {}",
                        state.user.getName(), voteName);
                return;
            }

            if (topic.removeVote(voteName, state.user)) {
                ctx.writeAndFlush("Голосование " + voteName + " успешно удалено\n");
                logger.info("Пользователь {} удалил голосование {} из раздела {}",
                        state.user.getName(), voteName, topicName);
            } else {
                ctx.writeAndFlush("Ошибка при удалении голосования\n");
            }
        }
    }

    /**
     * Обработка команды save
     */
    private void handleSave(ChannelHandlerContext ctx, String[] parts) {
        if (parts.length < 2) {
            ctx.writeAndFlush("Использование: save имя_файла\n");
            return;
        }

        String filename = parts[1];
        if (!filename.endsWith(".json")) {
            filename += ".json";
        }

        ServerApp.save(filename);
        ctx.writeAndFlush("Данные сохранены в файл " + filename + "\n");
    }

    /**
     * Обработка команды load
     */
    private void handleLoad(ChannelHandlerContext ctx, String[] parts) {
        if (parts.length < 2) {
            ctx.writeAndFlush("Использование: load имя_файла\n");
            return;
        }

        String filename = parts[1];
        if (!filename.endsWith(".json")) {
            filename += ".json";
        }

        try {
            ServerApp.load(filename);
            ctx.writeAndFlush("Данные загружены из файла " + filename + "\n");
        } catch (RuntimeException e) {
            ctx.writeAndFlush("Ошибка при загрузке данных: " + e.getMessage() + "\n");
        }
    }

    /**
     * Обработка команды exit
     */
    private void handleExit(ChannelHandlerContext ctx, ClientState state) {
        if (state.user != null) {
            ServerApp.logoutUser(state.user);
            logger.info("Пользователь {} завершил сеанс", state.user.getName());
        }
        ctx.writeAndFlush("До свидания!\n").addListener(future -> ctx.close());
        clientStates.remove(ctx);
    }

    /**
     * Сброс состояния создания голосования
     */
    private void resetVoteCreationState(ClientState state) {
        state.state = State.MENU;
        state.currentTopic = null;
        state.voteName = null;
        state.voteDescription = null;
        state.optionsCount = 0;
        state.voteOptions.clear();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        logger.info("Новое подключение: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ClientState state = clientStates.get(ctx);
        if (state != null && state.user != null) {
            ServerApp.logoutUser(state.user);
            logger.info("Пользователь {} отключился", state.user.getName());
        }
        clientStates.remove(ctx);
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ClientState state = clientStates.get(ctx);
        if (state != null && state.user != null) {
            logger.error("Ошибка у пользователя {}: {}", state.user.getName(), cause.getMessage(), cause);
        } else {
            logger.error("Ошибка подключения: {}", cause.getMessage(), cause);
        }
        ctx.close();
    }
}