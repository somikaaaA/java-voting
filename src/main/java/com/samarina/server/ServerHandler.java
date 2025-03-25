package com.samarina.server;

import com.samarina.model.Topic;
import com.samarina.model.Vote;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ServerHandler extends SimpleChannelInboundHandler<String>{ // определяем, что обработчик принимает только строки
    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);

    private enum CurrentState{ // список возможных состояний
        MENU,
        WAITING_FOR_NAME,
        WAITING_FOR_DESC,
        WAITING_FOR_QUANTITY,
        WAITING_FOR_OPTIONS,
        WAITING_FOR_VOTE
    }

    private static class ClientContext{ // вложенный класс для хранения введенных данных
        private String username;
        CurrentState currentState = CurrentState.MENU;
        boolean isLogged = false;
        String currentTopic;
        String voteName;
        String voteDescription;
        int optionsQuantity;
        Map<String, List<String>> voteOptions = new HashMap<>();
        List<String> currentOptions = new ArrayList<>();
    }

    private final Map<ChannelHandlerContext, ClientContext> clientContexts = new HashMap<>(); // храним существующие состояния клиентов

    private void handleCommand(ChannelHandlerContext ctx, String msg, ClientContext context){
        String[] messageParts = msg.split(" "); // для разделения строки на команды
        String command = messageParts[0]; // устанавливаем переменную для первой команды

        switch (command.toLowerCase()){
            case "login":
                if(context.isLogged){
                    logger.warn("Пользователь {} предпринял попытку повторной авторизации", context.username);
                    ctx.writeAndFlush("Вы уже авторизованы\n");
                    return;
                }
                handleLogin(ctx, messageParts, context);
                break;
            case "create":
                if(context.isLogged) {
                    handleCreate(ctx, messageParts, context);
                }else{
                    logger.warn("Неавторизованный пользователь попытался выполнить команду create");
                    ctx.writeAndFlush("Сперва необходимо авторизоваться с помощью команды login -u=username, где username - имя вашего пользователя\n");
                }
                break;
            case "view":
                if(context.isLogged) {
                    handleView(ctx, messageParts);
                }else{
                    logger.warn("Неавторизованный пользователь попытался выполнить команду view");
                    ctx.writeAndFlush("Сперва необходимо авторизоваться с помощью команды login -u=username, где username - имя вашего пользователя\n");
                }
                break;
            case "vote":
                if(context.isLogged) {
                    handleVote(ctx, messageParts, context);
                }else{
                    logger.warn("Неавторизованный пользователь попытался выполнить команду vote");
                    ctx.writeAndFlush("Сперва необходимо авторизоваться с помощью команды login -u=username, где username - имя вашего пользователя\n");
                }
                break;
            case "delete":
                if(context.isLogged) {
                    handleDelete(ctx, messageParts, context);
                }else{
                    logger.warn("Неавторизованный пользователь попытался выполнить команду delete");
                    ctx.writeAndFlush("Сперва необходимо авторизоваться с помощью команды login -u=username, где username - имя вашего пользователя\n");
                }
                break;
            case "help":
                handleHelp(ctx);
                break;
            case "exit":
                handleExit(ctx, context);
                break;
            case "save":
                if (messageParts.length > 1){
                    String filename = messageParts[1];
                    if(!filename.endsWith(".json")){ // делаем указание расширения файла необязательным
                        filename += ".json";
                    }
                    ServerApp.save(filename);
                    logger.warn("Данные сохранены в файл {}", filename);
                    ctx.writeAndFlush("Данные успешно сохранены в файл " + filename + "\n");
                }else{
                    logger.warn("При попытке сохранения данных не указано название файла");
                    ctx.writeAndFlush("Укажите название файла сохранения данных\n");
                }
                break;
            case "load":
                if (messageParts.length>1){
                    String filename = messageParts[1];
                    if(!filename.endsWith(".json")){ // делаем указание расширения файла необязательным
                        filename += ".json";
                    }

                    try {
                        ServerApp.load(filename);
                        logger.warn("Данные загружены из файла {}", filename);
                        ctx.writeAndFlush("Данные успешно загружены из файла " + filename + "\n");
                    }catch (RuntimeException e){
                        logger.error("При попытке загрузки из файла {} возникла ошибка: {}", filename, e.getMessage(), e);
                        ctx.writeAndFlush("Ошибка загрузки файла\n");
                    }
                }else{
                    logger.warn("При попытке загрузки данных не указано название файла");
                    ctx.writeAndFlush("Укажите название существующего файла для загрузки данных\n");
                }
                break;
            default:
                ctx.writeAndFlush("Неизвестная команда. Введите 'help' для списка команд.\n");
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, String[] messageParts, ClientContext context){
        if(messageParts.length > 1 && messageParts[1].split("=")[0].equals("-u")){
            if(messageParts[1].split("=").length == 2) {
                String username = messageParts[1].split("=")[1];
                if(!ServerApp.loginUser(username)){ // исключаем логин под одним пользователем с нескольких клиентов одновременно
                    logger.warn("Попытка повторного входа под пользователем: {}", username);
                    ctx.writeAndFlush("Пользователь " + username + " уже авторизован\n");
                    return;
                }
                context.isLogged = true;
                context.username = username;
                logger.info("Пользователь {} успешно вошел в систему", username);
                ctx.writeAndFlush("Вы вошли под пользователем " + username + "\n");
            }else{
                logger.warn("Ошибка ввода имени пользователя: {}", Arrays.toString(messageParts));
                ctx.writeAndFlush("Ошибка ввода имени пользователя username\n");
            }
        }else{
            logger.warn("Неправильно введена команда login: {}", Arrays.toString(messageParts));
            ctx.writeAndFlush("Неправильно введена команда login -u=username\n");
        }
    }

    private void handleCreate(ChannelHandlerContext ctx, String[] messageParts, ClientContext context){
        if (messageParts.length > 1 && messageParts[1].equalsIgnoreCase("topic")) {
            String topicName = messageParts[2].split("=")[1]; // получаем название раздела голосования
            synchronized (ServerApp.getTopics()) { // обеспечение потокобезопасности
                if (!ServerApp.getTopics().containsKey(topicName)) {
                    ServerApp.getTopics().put(topicName, new Topic(topicName)); // создаем новый раздел

                    logger.info("Пользователь {} создал раздел: {}", context.username, topicName);
                    ctx.writeAndFlush("Создан раздел голосования: " + topicName + "\n");
                } else {
                    logger.warn("Попытка создания дубликата раздела: {}", topicName);
                    ctx.writeAndFlush("Раздел с таким именем уже существует");
                }
            }
        }else if(messageParts.length > 1 && messageParts[1].equalsIgnoreCase("vote")){
            if(messageParts.length > 2 && messageParts[2].split("=")[0].equals("-t")){
                String topicName = messageParts[2].split("=")[1];
                synchronized (ServerApp.getTopics()) {
                    if (ServerApp.getTopics().containsKey(topicName)) {
                        context.currentTopic = topicName;
                        context.currentState = CurrentState.WAITING_FOR_NAME;
                        ctx.writeAndFlush("Создание нового голосования в разделе " + topicName + "\n Введите название голосования:");
                    } else {
                        logger.warn("Предупреждение: не удалось раздел {} во время выполнения команды create. Пользователь: {}", topicName, context.username);
                        ctx.writeAndFlush("Раздела с таким именем не существует\n");
                    }
                }
            }else {
                logger.warn("Предупреждение: некорректные параметры во время выполнения команды create. Пользователь: {}", context.username);
                ctx.writeAndFlush("Неправильно введена команда create vote -n=topic, где topic - название раздела голосования\n");
            }
        }else{
            logger.warn("Предупреждение: некорректный ввод команды create. Пользователь: {}", context.username);
            ctx.writeAndFlush("Для создания темы укажите ключевое слово topic\nДля создания голосования укажите ключевое слово vote и параметр темы -t=topic, где topic - название раздела\n");
        }
    }

    private void handleVoteCreation(ChannelHandlerContext ctx, String msg, ClientContext context) {
        synchronized (ServerApp.getTopics()) { // обеспечим потокобезопасность
            switch (context.currentState) {
                case WAITING_FOR_NAME:
                    if(ServerApp.getTopics().get(context.currentTopic).getVotes().containsKey(msg)){
                        logger.warn("Предупреждение: попытка создания голосования с уже существующим названием {}. Пользователь: {}", msg, context.username);
                        ctx.writeAndFlush("Голосование с таким названием уже существует");
                        return;
                    }
                    context.voteName = msg;
                    context.currentState = CurrentState.WAITING_FOR_DESC;
                    ctx.writeAndFlush("Введите описание к голосованию\n");
                    break;
                case WAITING_FOR_DESC:
                    context.voteDescription = msg;
                    context.currentState = CurrentState.WAITING_FOR_QUANTITY;
                    ctx.writeAndFlush("Введите количество возможных ответов\n");
                    break;
                case WAITING_FOR_QUANTITY:
                    try {
                        if (Integer.parseInt(msg) > 0) {
                            context.optionsQuantity = Integer.parseInt(msg);
                            context.currentState = CurrentState.WAITING_FOR_OPTIONS;
                            ctx.writeAndFlush("Введите вариант ответа 1\n");
                        } else {
                            logger.warn("Предупреждение: попытка создания голосования с {} вариантов ответа. Пользователь: {}", msg, context.username);
                            ctx.writeAndFlush("Должен быть хотя бы один вариант ответа\n");
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Предупреждение: попытка ввода {} в качестве числа ответов. Пользователь: {}", msg, context.username);
                        ctx.writeAndFlush("Ошибка ввода. Введите число возможных ответов\n");
                    }
                    break;
                case WAITING_FOR_OPTIONS:
                    if (!context.voteOptions.containsKey(msg.toLowerCase())) { // делаем каждый вариант ответа уникальным
                        context.voteOptions.put(msg, new ArrayList<>()); // добавляем опцию голосования, у которой пока что нет голосов
                    } else {
                        logger.warn("Предупреждение: попытка создания уже существующего варианта ответа {}. Пользователь: {}", msg, context.username);
                        ctx.writeAndFlush("Такой вариант ответа уже существует\nВведите другой вариант ответа " + (context.voteOptions.size() + 1) + "\n");
                    }
                    if (context.voteOptions.size() < context.optionsQuantity) {
                        ctx.writeAndFlush("Введите вариант ответа " + (context.voteOptions.size() + 1) + "\n");
                    } else { // добавляем новый раздел, очищаем данные внутреннего класса
                        Topic topic = ServerApp.getTopics().get(context.currentTopic);
                        topic.addVote(new Vote(context.voteName, context.voteDescription, context.voteOptions, context.username));
                        logger.info("Пользователь {} создал голосование {} в разделе {}", context.username, context.voteName, context.currentTopic);
                        ctx.writeAndFlush("Все варианты ответа записаны. Раздел голосования успешно создан\n");

                        context.currentState = CurrentState.MENU;
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

        for(String part : messageParts){ // записываем входные параметры
            if(part.startsWith("-t=") && part.split("=").length == 2){
                topicName = part.split("=")[1];
            }else if(part.startsWith("-v=") && part.split("=").length == 2){
                voteName = part.split("=")[1];
            }
        }

        synchronized (ServerApp.getTopics()) { // обеспечим потокобезопасность
            if (topicName != null && !ServerApp.getTopics().containsKey(topicName)) { // проверка на отсутствие раздела
                logger.warn("Предупреждение: не удалось найти раздел {} во время выполнения команды view. Пользователь: {}", topicName, clientContexts.get(ctx).username);
                ctx.writeAndFlush("Раздел с именем " + topicName + " не найден\n");
                return;
            }

            StringBuilder serverResponse = new StringBuilder();

            if (topicName == null && voteName == null) {// обработка команды view без параметров
                if (ServerApp.getTopics().isEmpty()) {
                    serverResponse.append("Не создано ни одного раздела\n");
                } else {
                    logger.info("Пользователь {} запросил список всех разделов", clientContexts.get(ctx).username);
                    serverResponse.append("Текущий список разделов:\n");
                    for (Map.Entry<String, Topic> topicEntry : ServerApp.getTopics().entrySet()) { // достаем и выводим каждый существующий раздел
                        serverResponse.append(topicEntry.getKey()).append(" (голосований в разделе: ").append(topicEntry.getValue().getVotes().size()).append(")\n");
                    }
                }
            } else if (topicName != null && voteName == null) { // обработка команды с параметром -t
                Topic topic = ServerApp.getTopics().get(topicName);
                logger.info("Пользователь {} запросил список голосований в разделе {}", clientContexts.get(ctx).username, topicName);
                serverResponse.append("Голосования в разделе: ").append(topicName).append(":\n");
                for (Map.Entry<String, Vote> voteEntry : topic.getVotes().entrySet()) {
                    serverResponse.append("- ").append(voteEntry.getKey()).append("\n");
                }
            } else if (topicName != null && voteName != null) { // обработка команды с параметрами -t, -v
                Topic topic = ServerApp.getTopics().get(topicName);
                Vote vote = topic.getVote(voteName);
                if (vote == null) {
                    logger.warn("Предупреждение: не удалось найти голосование {} в разделе {} во время выполнения команды view. Пользователь: {}", voteName, topicName, clientContexts.get(ctx).username);
                    ctx.writeAndFlush("Голосование " + voteName + " не найдено в разделе " + topicName + "\n");
                    return;
                }
                logger.info("Пользователь {} запросил детали голосования: {} в разделе: {}", clientContexts.get(ctx).username, voteName, topicName);
                serverResponse.append("Голосование ").append(voteName).append(":\n");
                serverResponse.append("Тема голосования: ").append(vote.getDescription()).append("\n").append("Варианты ответа:\n");
                for (Map.Entry<String, List<String>> optionEntry : vote.getOptions().entrySet()) {
                    serverResponse.append("- ").append(optionEntry.getKey()).append(". Проголосовавших пользователей: ").append(optionEntry.getValue().size()).append("\n");
                }
            } else {
                logger.warn("шибка: некорректный параметр во время выполнения команды view. Пользователь: {}", clientContexts.get(ctx).username);
                ctx.writeAndFlush("Неверно введена команда view. Список доступных команд:\n view\n view -t=topic\n view -t=topic -v=vote\n");
                return;
            }

            ctx.writeAndFlush(serverResponse.toString());
        }
    }

    private void handleVote(ChannelHandlerContext ctx, String[] messageParts, ClientContext context){
        String topicName = null;
        String voteName = null;

        for (String part : messageParts) { // записываем входные параметры
            if (part.startsWith("-t=") && part.split("=").length == 2) {
                topicName = part.split("=")[1];
            } else if (part.startsWith("-v=") && part.split("=").length == 2) {
                voteName = part.split("=")[1];
            }
        }

        if (topicName == null) {
            logger.warn("Предупреждение: некорректный параметр -t во время выполнения команды vote. Пользователь: {}", context.username);
            ctx.writeAndFlush("Не указано имя раздела. Используйте параметр -t=topic\n");
            return;
        }
        synchronized (ServerApp.getTopics()) { // обеспечим потокобезопасность
            if (!ServerApp.getTopics().containsKey(topicName)) { // проверка на отсутствие раздела
                logger.warn("Предупреждение: раздел {} не найден во время выполнения команды vote. Пользователь: {}", topicName, context.username);
                ctx.writeAndFlush("Раздел с именем " + topicName + " не найден\n");
                return;
            }

            Map<String, Vote> votes = ServerApp.getTopics().get(topicName).getVotes();

            if (voteName == null) {
                logger.warn("Предупреждение: некорректный параметр -v во время выполнения команды vote. Пользователь: {}", context.username);
                ctx.writeAndFlush("Не указано имя голосования. Используйте параметр -v=vote\n");
                return;
            }

            if (votes.containsKey(voteName)) {
                Map<String, List<String>> voteOptions = votes.get(voteName).getOptions();
                List<String> optionKeys = new ArrayList<>(voteOptions.keySet()); // помещаем ключи в список, чтобы мочь обращаться к опциям по индексу при обработке выбора

                boolean hasVoted = voteOptions.values().stream() // проверяем, не голосовал ли уже этот пользователь в голосовании
                        .anyMatch(userList -> userList.contains(context.username));

                if (hasVoted) {
                    logger.warn("Пользователь {} попытался повторно проголосовать в голосовании {} раздела {}", context.username, voteName, topicName);
                    ctx.writeAndFlush("Вы уже проголосовали в этом голосовании.\n");
                    return;
                }

                StringBuilder serverResponse = new StringBuilder("Вы перешли к голосованию с именем ").append(voteName).append(". Содержание голосования:\n").append(votes.get(voteName).getDescription()).append("\n");
                serverResponse.append("Чтобы проголосовать, введите цифру, соответствующую вашему варианту ответа\n");

                for (int i = 0; i < optionKeys.size(); i++) {
                    serverResponse.append(i + 1).append(". ").append(optionKeys.get(i)).append("\n");
                }

                ctx.writeAndFlush(serverResponse.toString());

                context.currentState = CurrentState.WAITING_FOR_VOTE;
                context.voteName = voteName;
                context.currentTopic = topicName;
                context.currentOptions = optionKeys;
            } else {
                logger.warn("Предупреждение: не удалось найти голосование {} в разделе {} во время выполнения команды vote. Пользователь: {}", voteName, topicName, context.username);
                ctx.writeAndFlush("Голосования " + voteName + " не существует в разделе " + topicName + "\n");
                return;
            }
        }
    }

    private void handleVoteChoice(ChannelHandlerContext ctx, String msg, ClientContext context){
        try {
            int choice = Integer.parseInt(msg);

            if(choice < 0 || choice > context.currentOptions.size()){
                logger.warn("Предупреждение: введенное число {} находится за границей допустимых вариантов ввода во время выполнения команды vote. Пользователь: {}", msg, context.username);
                ctx.writeAndFlush("Ошибка ввода. Вы должны ввести число от 1 до " + context.currentOptions.size() + "\n");
                return;
            }

            synchronized (ServerApp.getTopics()) { // обеспечим потокобезопасность
                String chosenOption = context.currentOptions.get(choice - 1);
                Topic topic = ServerApp.getTopics().get(context.currentTopic);
                Vote vote = topic.getVote(context.voteName);
                vote.vote(chosenOption, context.username);

                logger.info("Пользователь {} проголосовал в голосовании {} раздела {}", context.username, context.voteName, context.currentTopic);
                ctx.writeAndFlush("Вы успешно проголосовали за вариант под номером " + chosenOption + "\n");
            }

            context.currentState = CurrentState.MENU;
            context.voteName = null;
            context.currentTopic = null;
            context.currentOptions = null;
        } catch (NumberFormatException e) {
            logger.warn("Предупреждение: неверный ввод номера опции во время выполнения команды vote. Пользователь: {}", context.username);
            ctx.writeAndFlush("Ошибка ввода. Введите одно из доступных чисел\n");
        }
    }

    private void handleDelete(ChannelHandlerContext ctx, String[] messageParts, ClientContext context){
        String topicName = null;
        String voteName = null;

        for(String part : messageParts){ // записываем входные параметры
            if(part.startsWith("-t=") && part.split("=").length == 2){
                topicName = part.split("=")[1];
            }else if(part.startsWith("-v=") && part.split("=").length == 2){
                voteName = part.split("=")[1];
            }
        }

        if (topicName == null) {
            logger.warn("Предупреждение: не указан раздел во время выполнения команды delete. Пользователь: {}", context.username);
            ctx.writeAndFlush("Не указано имя раздела. Используйте параметр -t=topic\n");
            return;
        }

        synchronized (ServerApp.getTopics()) {
            if (!ServerApp.getTopics().containsKey(topicName)) { // проверка на отсутствие раздела
                logger.warn("Предупреждение: раздел {} не найден во время выполнения команды delete. Пользователь: {}", topicName, context.username);
                ctx.writeAndFlush("Раздел с именем " + topicName + " не найден\n");
                return;
            }

            Map<String, Vote> votes = ServerApp.getTopics().get(topicName).getVotes();

            if (voteName == null) {
                logger.warn("Предупреждение: не указано голосование во время выполнения команды delete. Пользователь: {}", context.username);
                ctx.writeAndFlush("Не указано имя голосования. Используйте параметр -v=vote\n");
                return;
            }
            if (votes.containsKey(voteName)) {
                if (context.username.equals(votes.get(voteName).getCreator())) {
                    ServerApp.getTopics().get(topicName).deleteVote(voteName);
                    logger.info("Пользователь {} удалил голосование {} из раздела {}", context.username, voteName, topicName);
                    ctx.writeAndFlush("Голосование " + voteName + " было удалено из раздела " + topicName + "\n");
                } else {
                    logger.warn("Предупреждение: Пользователь {} попытался удалить голосование {}, созданное пользователем {} в разделе {}", context.username, voteName, votes.get(voteName).getCreator(), topicName);
                    ctx.writeAndFlush("Ошибка доступа. Вы можете удалять только созданные вами голосования");
                    return;
                }
            } else {
                logger.warn("Предупреждение: не удалось найти голосование {} в разделе {} во время выполнения команды delete. Пользователь: {}", voteName, topicName, context.username);
                ctx.writeAndFlush("Голосования " + voteName + " не существует в разделе " + topicName + "\n");
                return;
            }
        }
    }

    private void handleExit(ChannelHandlerContext ctx, ClientContext context){
        ctx.writeAndFlush("Завершение работы\n").addListener(future -> {
            if (context.isLogged) {
                ServerApp.logoutUser(context.username); // удаляем пользователя из списка активных пользователей
                logger.info("Клиент {} (пользователь {}) завершил сеанс", ctx.channel().remoteAddress(), context.username);
                System.out.println("Пользователь " + context.username + " завершил сеанс");
            }
            clientContexts.remove(ctx);
            ctx.close();
        });
    }

    private void handleHelp(ChannelHandlerContext ctx) {
        StringBuilder helpMessage = new StringBuilder();
        helpMessage.append("Доступные команды:\n");
        helpMessage.append("------------------\n");

        // Команды для всех пользователей (до и после авторизации)
        helpMessage.append("• login -u=<username> – подключиться к серверу с указанным именем пользователя\n");
        helpMessage.append("• help – показать список доступных команд\n");
        helpMessage.append("• exit – завершить работу\n");

        // Команды, доступные только после авторизации
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
    protected  void channelRead0(ChannelHandlerContext ctx, String msg){
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
        logger.info("Клиент {} подключен", ctx.channel().remoteAddress());
        System.out.println("Клиент подключен: " + ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx){
        ClientContext context = clientContexts.get(ctx);

        if(context != null && context.isLogged){
            ServerApp.logoutUser(context.username);
            logger.info("Клиент {} (пользователь {}) отключен", ctx.channel().remoteAddress(), context.username);
            System.out.println("Соединение с пользователем " + context.username + " потеряно");
        }

        clientContexts.remove(ctx);
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause){
        ClientContext context = clientContexts.get(ctx);
        if (context != null && context.isLogged) {
            logger.error("Ошибка у пользователя {}: {}", context.username, cause.getMessage(), cause);
        } else {
            logger.error("Ошибка: {}", cause.getMessage(), cause);
        }
    }
}
