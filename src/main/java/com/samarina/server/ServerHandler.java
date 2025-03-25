package com.samarina.server;

import com.samarina.model.Topic;
import com.samarina.model.Vote;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ServerHandler extends SimpleChannelInboundHandler<String> {
    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);

    private enum state {
        MENU,
        WAITING_FOR_NAME,
        WAITING_FOR_DESC,
        WAITING_FOR_QUANTITY,
        WAITING_FOR_OPTIONS,
        WAITING_FOR_VOTE
    }

    private static class ClientContext {
        private String username;
        state currentState = state.MENU;
        boolean isLogged = false;
        String currentTopic;
        String voteName;
        String voteDescription;
        int optionsQuantity;
        Map<String, List<String>> voteOptions = new HashMap<>();
        List<String> currentOptions = new ArrayList<>();
    }

    private final Map<ChannelHandlerContext, ClientContext> clientContexts = new HashMap<>();

    private void handleCommand(ChannelHandlerContext channelHandlerContext, String message, ClientContext clientContext){
        String[] messageParts = message.split(" ");

        //переменная для первой команды
        String command = messageParts[0];

        switch (command.toLowerCase()){
            case "login":
                if(clientContext.isLogged){
                    logger.warn("Обнаружен повторный запрос авторизации от {}", clientContext.username);
                    channelHandlerContext.writeAndFlush("Это имя пользователя уже занято. Выберите другое\n");
                    return;
                }
                handleLogin(channelHandlerContext, messageParts, clientContext);
                break;
            case "create":
                if(clientContext.isLogged) {
                    handleCreate(channelHandlerContext, messageParts, clientContext);
                }else{
                    logger.warn("Неавторизованная попытка создания от {}", channelHandlerContext.channel().remoteAddress());
                    channelHandlerContext.writeAndFlush("Требуется авторизация. Введите login -u=ваше_имя\n");
                }
                break;
            case "view":
                if(clientContext.isLogged) {
                    handleView(channelHandlerContext, messageParts);
                }else{
                    logger.warn("Попытка просмотра без авторизации от {}", channelHandlerContext.channel().remoteAddress());
                    channelHandlerContext.writeAndFlush("Доступ запрещен. Сначала авторизуйтесь\n");
                }
                break;
            case "vote":
                if(clientContext.isLogged) {
                    handleVote(channelHandlerContext, messageParts, clientContext);
                }else{
                    logger.warn("Попытка голосования без авторизации от {}", channelHandlerContext.channel().remoteAddress());
                    channelHandlerContext.writeAndFlush("Голосовать могут только зарегистрированные пользователи\n");
                }
                break;
            case "delete":
                if(clientContext.isLogged) {
                    handleDelete(channelHandlerContext, messageParts, clientContext);
                }else{
                    logger.warn("Попытка удаления без авторизации от {}", channelHandlerContext.channel().remoteAddress());
                    channelHandlerContext.writeAndFlush("Удаление доступно после входа в систему\n");
                }
                break;
            case "help":
                handleHelp(channelHandlerContext);
                break;
            case "exit":
                handleExit(channelHandlerContext, clientContext);
                break;
            case "save":
                if (messageParts.length > 1){
                    String filename = messageParts[1];
                    if(!filename.endsWith(".json")){
                        filename += ".json";
                    }
                    ServerApp.save(filename);
                    logger.warn("Сохранение данных в {}", filename);
                    channelHandlerContext.writeAndFlush("Данные сохранены в " + filename + "\n");
                }else{
                    logger.warn("Пропущено имя файла для сохранения");
                    channelHandlerContext.writeAndFlush("Укажите название файла для сохранения\n");
                }
                break;
            case "load":
                if (messageParts.length>1){
                    String filename = messageParts[1];
                    if(!filename.endsWith(".json")){
                        filename += ".json";
                    }

                    try {
                        ServerApp.load(filename);
                        logger.warn("Загрузка данных из {}", filename);
                        channelHandlerContext.writeAndFlush("Данные восстановлены из " + filename + "\n");
                    }catch (RuntimeException e){
                        logger.error("Ошибка загрузки: {}", e.getMessage(), e);
                        channelHandlerContext.writeAndFlush("Не удалось загрузить данные\n");
                    }
                }else{
                    logger.warn("Не указано имя файла для загрузки");
                    channelHandlerContext.writeAndFlush("Укажите файл для загрузки\n");
                }
                break;
            default:
                channelHandlerContext.writeAndFlush("Неизвестная команда. Введите 'help' для вывода списка команд.\n");
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, String[] messageParts, ClientContext context){
        if(messageParts.length > 1 && messageParts[1].split("=")[0].equals("-u")){
            if(messageParts[1].split("=").length == 2) {
                String username = messageParts[1].split("=")[1];
                if(!ServerApp.loginNewUser(username)){
                    logger.warn("Повторная регистрация: {}", username);
                    ctx.writeAndFlush("Имя " + username + " уже используется\n");
                    return;
                }
                context.isLogged = true;
                context.username = username;
                logger.info("Новый участник: {}", username);
                ctx.writeAndFlush("Добро пожаловать, " + username + "!\n");
            }else{
                logger.warn("Некорректный формат имени: {}", Arrays.toString(messageParts));
                ctx.writeAndFlush("Ошибка: неправильный формат имени\n");
            }
        }else{
            logger.warn("Ошибка в команде login: {}", Arrays.toString(messageParts));
            ctx.writeAndFlush("Используйте: login -u=ваше_имя\n");
        }
    }

    private void handleCreate(ChannelHandlerContext ctx, String[] messageParts, ClientContext context){
        if (messageParts.length > 1 && messageParts[1].equalsIgnoreCase("topic")) {
            String topicName = messageParts[2].split("=")[1];
            synchronized (ServerApp.getTopics()) {
                if (!ServerApp.getTopics().containsKey(topicName)) {
                    ServerApp.getTopics().put(topicName, new Topic(topicName));

                    logger.info("Создан раздел {} участником {}", topicName, context.username);
                    ctx.writeAndFlush("Новый раздел создан: " + topicName + "\n");
                } else {
                    logger.warn("Дубликат раздела: {}", topicName);
                    ctx.writeAndFlush("Раздел с таким названием уже есть");
                }
            }
        }else if(messageParts.length > 1 && messageParts[1].equalsIgnoreCase("vote")){
            if(messageParts.length > 2 && messageParts[2].split("=")[0].equals("-t")){
                String topicName = messageParts[2].split("=")[1];
                synchronized (ServerApp.getTopics()) {
                    if (ServerApp.getTopics().containsKey(topicName)) {
                        context.currentTopic = topicName;
                        context.currentState = state.WAITING_FOR_NAME;
                        ctx.writeAndFlush("Создаем голосование в разделе " + topicName + "\nУкажите название:");
                    } else {
                        logger.warn("Отсутствует раздел {} при создании от {}", topicName, context.username);
                        ctx.writeAndFlush("Такого раздела не существует\n");
                    }
                }
            }else {
                logger.warn("Ошибка параметров create от {}", context.username);
                ctx.writeAndFlush("Формат: create vote -t=название_раздела\n");
            }
        }else{
            logger.warn("Некорректный create от {}", context.username);
            ctx.writeAndFlush("Для создания раздела: create topic -n=название\nДля голосования: create vote -t=раздел\n");
        }
    }

    private void handleVoteCreation(ChannelHandlerContext ctx, String msg, ClientContext context) {
        synchronized (ServerApp.getTopics()) {
            switch (context.currentState) {
                case WAITING_FOR_NAME:
                    if(ServerApp.getTopics().get(context.currentTopic).getVotes().containsKey(msg)){
                        logger.warn("Дубликат голосования {} от {}", msg, context.username);
                        ctx.writeAndFlush("Голосование с таким названием уже есть");
                        return;
                    }
                    context.voteName = msg;
                    context.currentState = state.WAITING_FOR_DESC;
                    ctx.writeAndFlush("Опишите суть голосования\n");
                    break;
                case WAITING_FOR_DESC:
                    context.voteDescription = msg;
                    context.currentState = state.WAITING_FOR_QUANTITY;
                    ctx.writeAndFlush("Сколько будет вариантов ответа?\n");
                    break;
                case WAITING_FOR_QUANTITY:
                    try {
                        if (Integer.parseInt(msg) > 0) {
                            context.optionsQuantity = Integer.parseInt(msg);
                            context.currentState = state.WAITING_FOR_OPTIONS;
                            ctx.writeAndFlush("Введите первый вариант\n");
                        } else {
                            logger.warn("Недопустимое число вариантов {} от {}", msg, context.username);
                            ctx.writeAndFlush("Нужен хотя бы один вариант\n");
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Нечисловой ввод вариантов от {}", context.username);
                        ctx.writeAndFlush("Введите число\n");
                    }
                    break;
                case WAITING_FOR_OPTIONS:
                    if (!context.voteOptions.containsKey(msg.toLowerCase())) {
                        context.voteOptions.put(msg, new ArrayList<>());
                    } else {
                        logger.warn("Дубликат варианта {} от {}", msg, context.username);
                        ctx.writeAndFlush("Такой вариант уже есть\nВведите вариант " + (context.voteOptions.size() + 1) + "\n");
                    }
                    if (context.voteOptions.size() < context.optionsQuantity) {
                        ctx.writeAndFlush("Введите вариант " + (context.voteOptions.size() + 1) + "\n");
                    } else {
                        Topic topic = ServerApp.getTopics().get(context.currentTopic);
                        topic.addVote(new Vote(context.voteName, context.voteDescription, context.voteOptions, context.username));
                        logger.info("Добавлено голосование {} в раздел {} от {}", context.voteName, context.currentTopic, context.username);
                        ctx.writeAndFlush("Голосование создано успешно\n");

                        context.currentState = state.MENU;
                        context.voteName = null;
                        context.currentTopic = null;
                        context.voteDescription = null;
                        context.optionsQuantity = 0;
                        if (context.voteOptions != null) {
                            context.voteOptions.clear();
                        }
                        if (context.currentOptions != null) {
                            context.currentOptions.clear();
                        }
                    }
                    break;
            }
        }
    }

    private void handleView(ChannelHandlerContext ctx, String[] messageParts){
        String topicName = null;
        String voteName = null;

        for(String part : messageParts){
            if(part.startsWith("-t=") && part.split("=").length == 2){
                topicName = part.split("=")[1];
            }else if(part.startsWith("-v=") && part.split("=").length == 2){
                voteName = part.split("=")[1];
            }
        }

        synchronized (ServerApp.getTopics()) {
            if (topicName != null && !ServerApp.getTopics().containsKey(topicName)) {
                logger.warn("Запрос несуществующего раздела {} от {}", topicName, clientContexts.get(ctx).username);
                ctx.writeAndFlush("Раздел " + topicName + " не найден\n");
                return;
            }

            StringBuilder serverResponse = new StringBuilder();

            if (topicName == null && voteName == null) {
                if (ServerApp.getTopics().isEmpty()) {
                    serverResponse.append("Нет созданных разделов\n");
                } else {
                    logger.info("Запрос списка разделов от {}", clientContexts.get(ctx).username);
                    serverResponse.append("Доступные разделы:\n");
                    for (Map.Entry<String, Topic> topicEntry : ServerApp.getTopics().entrySet()) {
                        serverResponse.append(topicEntry.getKey()).append(" (голосований: ").append(topicEntry.getValue().getVotes().size()).append(")\n");
                    }
                }
            } else if (topicName != null && voteName == null) {
                Topic topic = ServerApp.getTopics().get(topicName);
                logger.info("Запрос голосований в {} от {}", topicName, clientContexts.get(ctx).username);
                serverResponse.append("Голосования в ").append(topicName).append(":\n");
                for (Map.Entry<String, Vote> voteEntry : topic.getVotes().entrySet()) {
                    serverResponse.append("- ").append(voteEntry.getKey()).append("\n");
                }
            } else if (topicName != null && voteName != null) {
                Topic topic = ServerApp.getTopics().get(topicName);
                Vote vote = topic.getVote(voteName);
                if (vote == null) {
                    logger.warn("Запрос несуществующего голосования {} в {} от {}", voteName, topicName, clientContexts.get(ctx).username);
                    ctx.writeAndFlush("Голосование " + voteName + " не найдено\n");
                    return;
                }
                logger.info("Запрос деталей {} в {} от {}", voteName, topicName, clientContexts.get(ctx).username);
                serverResponse.append("Голосование: ").append(voteName).append("\n");
                serverResponse.append("Описание: ").append(vote.getDescription()).append("\nВарианты:\n");
                for (Map.Entry<String, List<String>> optionEntry : vote.getOptions().entrySet()) {
                    serverResponse.append("- ").append(optionEntry.getKey()).append(" (голосов: ").append(optionEntry.getValue().size()).append(")\n");
                }
            } else {
                logger.warn("Ошибка параметров view от {}", clientContexts.get(ctx).username);
                ctx.writeAndFlush("Используйте:\n view\n view -t=раздел\n view -t=раздел -v=голосование\n");
                return;
            }

            ctx.writeAndFlush(serverResponse.toString());
        }
    }

    private void handleVote(ChannelHandlerContext ctx, String[] messageParts, ClientContext context){
        String topicName = null;
        String voteName = null;

        for (String part : messageParts) {
            if (part.startsWith("-t=") && part.split("=").length == 2) {
                topicName = part.split("=")[1];
            } else if (part.startsWith("-v=") && part.split("=").length == 2) {
                voteName = part.split("=")[1];
            }
        }

        if (topicName == null) {
            logger.warn("Пропущен раздел в vote от {}", context.username);
            ctx.writeAndFlush("Укажите раздел параметром -t=название\n");
            return;
        }
        synchronized (ServerApp.getTopics()) {
            if (!ServerApp.getTopics().containsKey(topicName)) {
                logger.warn("Попытка голосования в несуществующем разделе {} от {}", topicName, context.username);
                ctx.writeAndFlush("Раздел " + topicName + " не существует\n");
                return;
            }

            Map<String, Vote> votes = ServerApp.getTopics().get(topicName).getVotes();

            if (voteName == null) {
                logger.warn("Пропущено голосование в vote от {}", context.username);
                ctx.writeAndFlush("Укажите голосование параметром -v=название\n");
                return;
            }

            if (votes.containsKey(voteName)) {
                Map<String, List<String>> voteOptions = votes.get(voteName).getOptions();
                List<String> optionKeys = new ArrayList<>(voteOptions.keySet());

                boolean hasVoted = voteOptions.values().stream()
                        .anyMatch(userList -> userList.contains(context.username));

                if (hasVoted) {
                    logger.warn("Повторное голосование {} в {} от {}", voteName, topicName, context.username);
                    ctx.writeAndFlush("Вы уже участвовали в этом голосовании.\n");
                    return;
                }

                StringBuilder serverResponse = new StringBuilder("Голосование: ").append(voteName).append("\nТема: ").append(votes.get(voteName).getDescription()).append("\n");
                serverResponse.append("Выберите вариант (введите номер):\n");

                for (int i = 0; i < optionKeys.size(); i++) {
                    serverResponse.append(i + 1).append(". ").append(optionKeys.get(i)).append("\n");
                }

                ctx.writeAndFlush(serverResponse.toString());

                context.currentState = state.WAITING_FOR_VOTE;
                context.voteName = voteName;
                context.currentTopic = topicName;
                context.currentOptions = optionKeys;
            } else {
                logger.warn("Попытка голосования в несуществующем {} в {} от {}", voteName, topicName, context.username);
                ctx.writeAndFlush("Голосование " + voteName + " не найдено\n");
                return;
            }
        }
    }

    private void handleVoteChoice(ChannelHandlerContext ctx, String msg, ClientContext context){
        try {
            int choice = Integer.parseInt(msg);

            if(choice < 0 || choice > context.currentOptions.size()){
                logger.warn("Некорректный выбор {} в vote от {}", msg, context.username);
                ctx.writeAndFlush("Введите число от 1 до " + context.currentOptions.size() + "\n");
                return;
            }

            synchronized (ServerApp.getTopics()) {
                String chosenOption = context.currentOptions.get(choice - 1);
                Topic topic = ServerApp.getTopics().get(context.currentTopic);
                Vote vote = topic.getVote(context.voteName);
                vote.addVote(chosenOption, context.username);

                logger.info("Голос {} в {} от {}", chosenOption, context.voteName, context.username);
                ctx.writeAndFlush("Ваш выбор: " + chosenOption + " учтен\n");
            }

            context.currentState = state.MENU;
            context.voteName = null;
            context.currentTopic = null;
            context.currentOptions = null;
        } catch (NumberFormatException e) {
            logger.warn("Ошибка ввода номера от {}", context.username);
            ctx.writeAndFlush("Введите номер варианта\n");
        }
    }

    private void handleDelete(ChannelHandlerContext ctx, String[] messageParts, ClientContext context){
        String topicName = null;
        String voteName = null;

        for(String part : messageParts){
            if(part.startsWith("-t=") && part.split("=").length == 2){
                topicName = part.split("=")[1];
            }else if(part.startsWith("-v=") && part.split("=").length == 2){
                voteName = part.split("=")[1];
            }
        }

        if (topicName == null) {
            logger.warn("Пропущен раздел в delete от {}", context.username);
            ctx.writeAndFlush("Укажите раздел параметром -t=название\n");
            return;
        }

        synchronized (ServerApp.getTopics()) {
            if (!ServerApp.getTopics().containsKey(topicName)) {
                logger.warn("Попытка удаления в несуществующем разделе {} от {}", topicName, context.username);
                ctx.writeAndFlush("Раздел " + topicName + " не найден\n");
                return;
            }

            Map<String, Vote> votes = ServerApp.getTopics().get(topicName).getVotes();

            if (voteName == null) {
                logger.warn("Пропущено голосование в delete от {}", context.username);
                ctx.writeAndFlush("Укажите голосование параметром -v=название\n");
                return;
            }
            if (votes.containsKey(voteName)) {
                if (context.username.equals(votes.get(voteName).getCreator())) {
                    ServerApp.getTopics().get(topicName).deleteVote(voteName);
                    logger.info("Удаление {} в {} от {}", voteName, topicName, context.username);
                    ctx.writeAndFlush("Голосование " + voteName + " удалено\n");
                } else {
                    logger.warn("Попытка удаления чужого голосования {} от {}", voteName, context.username);
                    ctx.writeAndFlush("Можно удалять только свои голосования");
                    return;
                }
            } else {
                logger.warn("Попытка удаления несуществующего {} в {} от {}", voteName, topicName, context.username);
                ctx.writeAndFlush("Голосование " + voteName + " не найдено\n");
                return;
            }
        }
    }

    private void handleExit(ChannelHandlerContext ctx, ClientContext context){
        ctx.writeAndFlush("До свидания!\n").addListener(future -> {
            if (context.isLogged) {
                ServerApp.logoutUser(context.username);
                logger.info("Отключение участника {}", context.username);
                System.out.println("Участник " + context.username + " вышел");
            }
            clientContexts.remove(ctx);
            ctx.close();
        });
    }

    private void handleHelp(ChannelHandlerContext ctx) {
        StringBuilder helpMessage = new StringBuilder();
        helpMessage.append("Доступные команды:\n");
        helpMessage.append("------------------\n");

        helpMessage.append("• login -u=<username> – подключиться к серверу с указанным именем пользователя\n");
        helpMessage.append("• help – показать список доступных команд\n");
        helpMessage.append("• exit – завершить работу\n");

        helpMessage.append("\nПосле авторизации доступны:\n");
        helpMessage.append("• create topic -n=<topic> – создать новый раздел\n");
        helpMessage.append("• create vote -t=<topic> – создать голосование в указанном разделе\n");
        helpMessage.append("• view – показать список разделов\n");
        helpMessage.append("• view -t=<topic> – показать голосования в разделе\n");
        helpMessage.append("• view -t=<topic> -v=<vote> – показать детали голосования\n");
        helpMessage.append("• vote -t=<topic> -v=<vote> – проголосовать\n");
        helpMessage.append("• delete -t=<topic> -v=<vote> – удалить голосование (только создатель)\n");

        helpMessage.append("\nСерверные команды:\n");
        helpMessage.append("• save <filename> – сохранить данные в файл\n");
        helpMessage.append("• load <filename> – загрузить данные из файла\n");

        ctx.writeAndFlush(helpMessage.toString());
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, String msg){
        ClientContext context = clientContexts.computeIfAbsent(ctx, key -> new ClientContext()); // получаем состояние, если оно уже есть, или записываем, если еще нет

        switch(context.currentState){
            case MENU:
                handleCommand(ctx, msg, context);
                break;
            case WAITING_FOR_VOTE:
                handleVoteChoice(ctx, msg, context);
                break;
            default:
                handleVoteCreation(ctx, msg, context);
                break;
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        logger.info("Подключение: {}", ctx.channel().remoteAddress());
        System.out.println("Новое подключение: " + ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx){
        ClientContext context = clientContexts.get(ctx);

        if(context != null && context.isLogged){
            ServerApp.logoutUser(context.username);
            logger.info("Отключение: {} ({})", ctx.channel().remoteAddress(), context.username);
            System.out.println(context.username + " отключился");
        }

        clientContexts.remove(ctx);
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause){
        ClientContext context = clientContexts.get(ctx);
        if (context != null && context.isLogged) {
            logger.error("Сбой у {}: {}", context.username, cause.getMessage(), cause);
        } else {
            logger.error("Ошибка соединения: {}", cause.getMessage(), cause);
        }
    }
}