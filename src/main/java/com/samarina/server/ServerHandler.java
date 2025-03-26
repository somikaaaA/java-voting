package com.samarina.server;

import com.samarina.model.Topic;
import com.samarina.model.Vote;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ServerHandler extends SimpleChannelInboundHandler<String> {
    private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

    //класс для хранения состояния для каждого клиента
    private static class CurrentContext {
        private String name;
        State state = State.MENU;
        boolean isLogin = false;
        String currentTopic;
        String voteName;
        String voteDescription;
        int numOfOptions;
        Map<String, List<String>> voteOptions = new HashMap<>();
        List<String> currentOptions = new ArrayList<>();
    }

    //хранение состояний клиентов
    private final Map<ChannelHandlerContext, CurrentContext> currentClientCtx = new HashMap<>();

    private void handleCommand(ChannelHandlerContext ctx, String msg, CurrentContext context){
        String[] splitMessage = msg.split(" ");
        String command = splitMessage[0];

        switch (command){
            case "login":
                if(context.isLogin){
                    log.warn("Повторная авторизация. Пользователь {}", context.name);
                    ctx.writeAndFlush("Вы уже авторизованы\n");
                    return;
                }
                handleLogin(ctx, splitMessage, context);
                break;
            case "create":
                if(context.isLogin) {
                    handleCreate(ctx, splitMessage, context);
                }else{
                    log.warn("Попытка выполнения команды create неавторизованным пользователем");
                    ctx.writeAndFlush("Для выполнения команды create необходимо авторизоваться\n Для авторизации выполните команду login");
                }
                break;
            case "view":
                if(context.isLogin) {
                    handleView(ctx, splitMessage);
                }else{
                    log.warn("Попытка выполнения команды view неавторизованным пользователем");
                    ctx.writeAndFlush("Для выполнения команды view необходимо авторизоваться\n Для авторизации выполните команду login");
                }
                break;
            case "vote":
                if(context.isLogin) {
                    handleVote(ctx, splitMessage, context);
                }else{
                    log.warn("Попытка выполнения команды vote неавторизованным пользователем");
                    ctx.writeAndFlush("Для выполнения команды vote необходимо авторизоваться\n Для авторизации выполните команду login");
                }
                break;
            case "delete":
                if(context.isLogin) {
                    handleDelete(ctx, splitMessage, context);
                }else{
                    log.warn("Попытка выполнения команды delete неавторизованным пользователем");
                    ctx.writeAndFlush("Для выполнения команды delete необходимо авторизоваться\n Для авторизации выполните команду login");
                }
                break;
            case "help":
                handleHelp(ctx);
                break;
            case "exit":
                handleExit(ctx, context);
                break;
            case "save":
                if (splitMessage.length > 1){
                    String filename = splitMessage[1];
                    if(!filename.endsWith(".json")) {
                        filename += ".json";
                    }
                    ServerApp.save(filename);
                    log.warn("Данные сохранены в файл {}", filename);
                    ctx.writeAndFlush("Данные успешно сохранены в файл " + filename + "\n");
                }else{
                    log.warn("При попытке сохранения данных не указано название файла");
                    ctx.writeAndFlush("Укажите название файла сохранения данных\n");
                }
                break;
            case "load":
                if (splitMessage.length>1){
                    String filename = splitMessage[1];
                    if(!filename.endsWith(".json")) {
                        filename += ".json";
                    }

                    try {
                        ServerApp.load(filename);
                        log.warn("Данные загружены из файла {}", filename);
                        ctx.writeAndFlush("Данные успешно загружены из файла " + filename + "\n");
                    }catch (RuntimeException e){
                        log.error("Возникла ошибка при попытке загрузки из файла: {}", e.getMessage(), e);
                        ctx.writeAndFlush("Ошибка загрузки из файла\n");
                    }
                }else{
                    log.warn("При попытке загрузки данных не указано название файла");
                    ctx.writeAndFlush("Укажите название существующего файла для загрузки данных\n");
                }
                break;
            default:
                log.warn("Пользователь ввел некорректную команду {}", msg);
                ctx.writeAndFlush("Неизвестная команда. Введите 'help' для вывода списка команд\n");
        }
    }

    private void handleHelp(ChannelHandlerContext ctx) {
        String helpMessage = "Доступные команды:\n" +
                "------------------\n" +
                "• login -u=<username> – подключиться к серверу с указанным именем пользователя\n" +
                "• help – показать список доступных команд\n" +
                "• exit – завершить работу\n" +
                "\nПосле авторизации доступны:\n" +
                "• create topic -n=<topic> – создать новый раздел\n" +
                "• create vote -t=<topic> – создать голосование в указанном разделе\n" +
                "• view – показать список разделов\n" +
                "• view -t=<topic> – показать голосования в разделе\n" +
                "• view -t=<topic> -v=<vote> – показать детали голосования\n" +
                "• vote -t=<topic> -v=<vote> – проголосовать\n" +
                "• delete -t=<topic> -v=<vote> – удалить голосование (только создатель)\n" +
                "\nСерверные команды:\n" +
                "• save <filename> – сохранить данные в файл\n" +
                "• load <filename> – загрузить данные из файла\n";

        ctx.writeAndFlush(helpMessage);
    }

    private void handleLogin(ChannelHandlerContext ctx, String[] messageParts, CurrentContext context) {
        if(messageParts.length > 1 && messageParts[1].split("=")[0].equals("-u")){
            if(messageParts[1].split("=").length == 2) {
                String username = messageParts[1].split("=")[1];
                if(!ServerApp.loginUser(username)) {
                    log.warn("Повторная регистрация: {}", username);
                    ctx.writeAndFlush("Имя " + username + " уже используется\n");
                    return;
                }
                context.isLogin = true;
                context.name = username;
                log.info("Пользователь {} подключен", username);
                ctx.writeAndFlush("Вы вошли в систему. Login: " + username + "\n");
            }else{
                log.warn("Ошибка ввода имени пользователя: {}", Arrays.toString(messageParts));
                ctx.writeAndFlush("Ошибка ввода имени пользователя username\n");
            }
        }else{
            log.warn("Неправильно введена команда login: {}", Arrays.toString(messageParts));
            ctx.writeAndFlush("Неправильно введена команда login -u=username\n");
        }
    }

    private void handleCreate(ChannelHandlerContext ctx, String[] messageParts, CurrentContext context) {
        if (messageParts.length > 1 && messageParts[1].equalsIgnoreCase("topic")) {
            String topicName = messageParts[2].split("=")[1];
            synchronized (ServerApp.getTopics()) {
                if (!ServerApp.getTopics().containsKey(topicName)) {
                    ServerApp.getTopics().put(topicName, new Topic(topicName));

                    log.info("Создан раздел: {}. Пользователь: {}", topicName, context.name);
                    ctx.writeAndFlush("Создан новый раздел " + topicName + "\n");
                } else {
                    log.warn("Попытка повторного создания раздела: {}", topicName);
                    ctx.writeAndFlush("Раздел с таким именем уже существует");
                }
            }
        }else if(messageParts.length > 1 && messageParts[1].equalsIgnoreCase("vote")) {
            if(messageParts.length > 2 && messageParts[2].split("=")[0].equals("-t")){
                String topicName = messageParts[2].split("=")[1];
                synchronized (ServerApp.getTopics()) {
                    if (ServerApp.getTopics().containsKey(topicName)) {
                        context.currentTopic = topicName;
                        context.state = State.WAITING_FOR_NAME;
                        ctx.writeAndFlush("Создание голосования в разделе " + topicName + "\n Введите название голосования:");
                    } else {
                        log.warn("Раздел для создания голосования {} не найден. Пользователь: {}", topicName, context.name);
                        ctx.writeAndFlush("Такого  раздела не существует\n");
                    }
                }
            }else {
                log.warn("Введены некорректные параметры. Команда create. Пользователь: {}", context.name);
                ctx.writeAndFlush("Команда введена некорректно. Попробуйте еще раз\n");
            }
        }else{
            log.warn("Некорректный ввод команды create. Пользователь: {}", context.name);
            ctx.writeAndFlush("Для создания темы укажите ключевое слово topic\nДля создания голосования укажите ключевое слово vote и параметр темы -t=topic\n");
        }
    }

    private void handleVoteCreation(ChannelHandlerContext ctx, String msg, CurrentContext context) {
        synchronized (ServerApp.getTopics()) {
            switch (context.state) {
                case WAITING_FOR_NAME:
                    if(ServerApp.getTopics().get(context.currentTopic).getAllVotes().containsKey(msg)) {
                        log.warn("Попытка повторного создания голосования. Пользователь {}", context.name);
                        ctx.writeAndFlush("Голосование с таким названием уже существует");
                        return;
                    }
                    context.voteName = msg;
                    context.state = State.WAITING_FOR_DESC;
                    ctx.writeAndFlush("Введите описание к голосованию\n");
                    break;
                case WAITING_FOR_DESC:
                    context.voteDescription = msg;
                    context.state = State.WAITING_FOR_QUANTITY;
                    ctx.writeAndFlush("Введите количество возможных ответов\n");
                    break;
                case WAITING_FOR_QUANTITY:
                    try {
                        if (Integer.parseInt(msg) > 0) {
                            context.numOfOptions = Integer.parseInt(msg);
                            context.state = State.WAITING_FOR_OPTIONS;
                            ctx.writeAndFlush("Введите вариант ответа 1\n");
                        } else {
                            log.warn("Попытка создания голосования без ответов {}", context.name);
                            ctx.writeAndFlush("Должен быть хотя бы один вариант ответа\n");
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Ошибка ввода: {}. Пользователь {}", msg, context.name);
                        ctx.writeAndFlush("Ошибка ввода. Введите число возможных ответов\n");
                    }
                    break;
                case WAITING_FOR_OPTIONS:
                    if (!context.voteOptions.containsKey(msg.toLowerCase())) {
                        context.voteOptions.put(msg, new ArrayList<>());
                    } else {
                        log.warn("Попытка повторного создания варианта ответа: {} Пользователь: {}", msg, context.name);
                        ctx.writeAndFlush("Такой вариант ответа уже существует\nВведите другой вариант ответа " + (context.voteOptions.size() + 1) + "\n");
                    }
                    if (context.voteOptions.size() < context.numOfOptions) {
                        ctx.writeAndFlush("Введите вариант ответа " + (context.voteOptions.size() + 1) + "\n");
                    } else {
                        Topic topic = ServerApp.getTopics().get(context.currentTopic);
                        topic.addVote(new Vote(context.voteName, context.voteDescription, context.voteOptions, context.name));
                        log.info("Пользователь {} создал голосование {} в разделе {}", context.name, context.voteName, context.currentTopic);
                        ctx.writeAndFlush("Новый раздел голосования успешно создан\n");

                        context.state = State.MENU;
                        context.voteName = null;
                        context.currentTopic = null;
                        context.voteDescription = null;
                        context.numOfOptions = 0;
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

    private void handleView(ChannelHandlerContext ctx, String[] messageParts) {
        String topicName = null;
        String voteName = null;

        for(String part : messageParts){ // записываем входные параметры
            if(part.startsWith("-t=") && part.split("=").length == 2){
                topicName = part.split("=")[1];
            }else if(part.startsWith("-v=") && part.split("=").length == 2){
                voteName = part.split("=")[1];
            }
        }

        synchronized (ServerApp.getTopics()) {
            if (topicName != null && !ServerApp.getTopics().containsKey(topicName)) {
                log.warn("Не удалось найти раздел {} во время выполнения команды view пользователем {}", topicName, currentClientCtx.get(ctx).name);
                log.warn("Раздел {} не найден. Команды view. Пользователь {}", topicName, currentClientCtx.get(ctx).name);
                ctx.writeAndFlush("Раздел " + topicName + " не найден\n");
                return;
            }

            StringBuilder serverResponse = new StringBuilder();

            if (topicName == null && voteName == null) {
                if (ServerApp.getTopics().isEmpty()) {
                    serverResponse.append("Не создано ни одного раздела\n");
                } else {
                    log.info("Запрос всех разделов. Пользователь {}", currentClientCtx.get(ctx).name);
                    serverResponse.append("Текущий список разделов:\n");
                    for (Map.Entry<String, Topic> topicEntry : ServerApp.getTopics().entrySet()) {
                        serverResponse.append(topicEntry.getKey()).append(" (голосований в разделе: ").append(topicEntry.getValue().getAllVotes().size()).append(")\n");
                    }
                }
            } else if (topicName != null && voteName == null) {
                Topic topic = ServerApp.getTopics().get(topicName);
                log.info("Запрос всех голосований в разделе {}. Пользователь {}", topicName, currentClientCtx.get(ctx).name);
                serverResponse.append("Голосования в разделе: ").append(topicName).append(":\n");
                for (Map.Entry<String, Vote> voteEntry : topic.getAllVotes().entrySet()) {
                    serverResponse.append("- ").append(voteEntry.getKey()).append("\n");
                }
            } else if (topicName != null && voteName != null) {
                Topic topic = ServerApp.getTopics().get(topicName);
                Vote vote = topic.getVote(voteName);
                if (vote == null) {
                    log.warn("Голосование {} в разделе {} не найдено. Команда view. Пользователь: {}", voteName, topicName, currentClientCtx.get(ctx).name);
                    ctx.writeAndFlush("Голосование " + voteName + " не найдено в разделе " + topicName + "\n");
                    return;
                }
                log.info("Запрос информации о голосовании {} в разделе {}. Пользователь: {}",voteName, topicName, currentClientCtx.get(ctx).name);
                serverResponse.append("Голосование ").append(voteName).append(":\n");
                serverResponse.append("Тема голосования: ").append(vote.getDescription()).append("\n").append("Варианты ответа:\n");
                for (Map.Entry<String, List<String>> optionEntry : vote.getOptions().entrySet()) {
                    serverResponse.append("- ").append(optionEntry.getKey()).append(". Проголосовавших пользователей: ").append(optionEntry.getValue().size()).append("\n");
                }
            } else {
                log.warn("Ввод некорректных параметров. Пользователь: {}", currentClientCtx.get(ctx).name);
                ctx.writeAndFlush("Неверно введена команда view\n");
                return;
            }

            ctx.writeAndFlush(serverResponse.toString());
        }
    }

    private void handleVote(ChannelHandlerContext ctx, String[] messageParts, CurrentContext context) {
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
            log.warn("Ввод некорректных параметров. Команда vote. Пользователь: {}", context.name);
            ctx.writeAndFlush("Не указано имя раздела\n");
            return;
        }
        synchronized (ServerApp.getTopics()) {
            if (!ServerApp.getTopics().containsKey(topicName)) {
                log.warn("Раздел {} не найден. Команда vote. Пользователь: {}", topicName, context.name);
                ctx.writeAndFlush("Раздел " + topicName + " не найден\n");
                return;
            }

            Map<String, Vote> votes = ServerApp.getTopics().get(topicName).getAllVotes();

            if (voteName == null) {
                log.warn("Некорректный параметр -v. Команда vote. Пользователь: {}", context.name);
                ctx.writeAndFlush("Не указано имя голосования\n");
                return;
            }

            if (votes.containsKey(voteName)) {
                Map<String, List<String>> voteOptions = votes.get(voteName).getOptions();
                List<String> optionKeys = new ArrayList<>(voteOptions.keySet());

                boolean hasVoted = voteOptions.values().stream()
                        .anyMatch(userList -> userList.contains(context.name));

                if (hasVoted) {
                    log.warn("Попытка повторного голосования {} в разделе {}. Пользователь {}", voteName, topicName, context.name);
                    ctx.writeAndFlush("Вы уже голосовали в этом голосовании.\n");
                    return;
                }

                StringBuilder serverResponse = new StringBuilder("Вы перешли к голосованию ").append(voteName).append(". Голосование:\n").append(votes.get(voteName).getDescription()).append("\n");
                serverResponse.append("Чтобы проголосовать, введите цифру варианта ответа\n");

                for (int i = 0; i < optionKeys.size(); i++) {
                    serverResponse.append(i + 1).append(". ").append(optionKeys.get(i)).append("\n");
                }

                ctx.writeAndFlush(serverResponse.toString());

                context.state = State.WAITING_FOR_VOTE;
                context.voteName = voteName;
                context.currentTopic = topicName;
                context.currentOptions = optionKeys;
            } else {
                log.warn("Не удалось найти голосование {} в разделе {} во время выполнения команды vote пользователем {}", voteName, topicName, context.name);
                log.warn("Голосование {} в разделе {} не найдено. Команда vote. Пользователь {}", voteName, topicName, context.name);
                ctx.writeAndFlush("Голосование " + voteName + " не найдено в разделе " + topicName + "\n");
                return;
            }
        }
    }

    private void handleVoteChoice(ChannelHandlerContext ctx, String msg, CurrentContext context) {
        try {
            int choice = Integer.parseInt(msg);

            if(choice < 0 || choice > context.currentOptions.size()) {
                log.warn("Ввод некорректного числа {}. Команда vote. Пользователь: {}", msg, context.name);
                ctx.writeAndFlush("Введите число от 1 до " + context.currentOptions.size() + "\n");
                return;
            }

            synchronized (ServerApp.getTopics()) {
                String chosenOption = context.currentOptions.get(choice - 1);
                Topic topic = ServerApp.getTopics().get(context.currentTopic);
                Vote vote = topic.getVote(context.voteName);
                vote.vote(chosenOption, context.name);

                log.info("Пользователь {} проголосовал в голосовании {} раздела {}", context.name, context.voteName, context.currentTopic);
                ctx.writeAndFlush("Ваш голос засчитан в голосовании\n");
            }

            context.state = State.MENU;
            context.voteName = null;
            context.currentTopic = null;
            context.currentOptions = null;
        } catch (NumberFormatException e) {
            log.warn("Ошибка ввода. Команда vote. Пользователь: {}", context.name);
            ctx.writeAndFlush("Ошибка ввода. Введите одно из доступных чисел\n");
        }
    }

    private void handleDelete(ChannelHandlerContext ctx, String[] messageParts, CurrentContext context) {
        String topicName = null;
        String voteName = null;

        for(String part : messageParts) {
            if(part.startsWith("-t=") && part.split("=").length == 2){
                topicName = part.split("=")[1];
            }else if(part.startsWith("-v=") && part.split("=").length == 2){
                voteName = part.split("=")[1];
            }
        }

        if (topicName == null) {
            log.warn("Не указано название раздела. Команда delete. Пользователь: {}", context.name);
            ctx.writeAndFlush("Не указано название раздела\n");
            return;
        }

        synchronized (ServerApp.getTopics()) {
            if (!ServerApp.getTopics().containsKey(topicName)) {
                log.warn("Раздел {} не найден. Команда delete. Пользователь: {}", topicName, context.name);
                ctx.writeAndFlush("Раздел " + topicName + " не найден\n");
                return;
            }

            Map<String, Vote> votes = ServerApp.getTopics().get(topicName).getAllVotes();

            if (voteName == null) {
                log.warn("Не указано название голосования. Команда delete. Пользователь: {}", context.name);
                ctx.writeAndFlush("Не указано название голосования.\n");
                return;
            }
            if (votes.containsKey(voteName)) {
                if (context.name.equals(votes.get(voteName).getCreator())) {
                    ServerApp.getTopics().get(topicName).deleteVote(voteName);
                    log.info("Удалено голосование {} из раздела {}. Пользователь {}", voteName, topicName, context.name);
                    ctx.writeAndFlush("Голосование " + voteName + " удалено из раздела" + "\n");
                } else {
                    log.warn("Ошибка прав доступа. Команда delete. Пользователь: {}", context.name);
                    ctx.writeAndFlush("Ошибка доступа. Вы не можете удалить это голосование");
                    return;
                }
            } else {
                log.warn("Голосование {} в разделе {} не найдено. Команда delete. Пользователь: {}", voteName, topicName, context.name);
                ctx.writeAndFlush("Голосования " + voteName + " не существует в разделе " + topicName + "\n");
                return;
            }
        }
    }

    private void handleExit(ChannelHandlerContext ctx, CurrentContext context){
        ctx.writeAndFlush("Завершение работы\n").addListener(future -> {
            if (context.isLogin) {
                ServerApp.logoutUser(context.name);
                log.info("Клиент {} (пользователь {}) отключен", ctx.channel().remoteAddress(), context.name);
                System.out.println("Пользователь " + context.name + " отключен");
            }
            currentClientCtx.remove(ctx);
            ctx.close();
        });
    }

    @Override
    protected  void channelRead0(ChannelHandlerContext ctx, String msg){
        //получение/создание контекста для текущего клиента
        CurrentContext context = currentClientCtx.computeIfAbsent(ctx, key -> new CurrentContext());

        //обработка сообщения в зависимости от состояния
        switch(context.state){
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

    //обработка нового подключения
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("Клиент {} подключен", ctx.channel().remoteAddress());
        System.out.println("Клиент подключен: " + ctx.channel().remoteAddress());
    }

    //обработка отключения клиента
    @Override
    public void channelInactive(ChannelHandlerContext ctx){
        CurrentContext context = currentClientCtx.get(ctx);

        if(context != null && context.isLogin){
            ServerApp.logoutUser(context.name);
            log.info("Клиент {} (пользователь {}) отключен", ctx.channel().remoteAddress(), context.name);
            System.out.println("Соединение с пользователем " + context.name + " разорвано");
        }

        currentClientCtx.remove(ctx);
        ctx.close();
    }

    //обработка ошибок
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause){
        CurrentContext context = currentClientCtx.get(ctx);
        if (context != null && context.isLogin) {
            log.error("Ошибка у пользователя {}: {}", context.name, cause.getMessage(), cause);
        } else {
            log.error("Ошибка: {}", cause.getMessage(), cause);
        }
    }
}
