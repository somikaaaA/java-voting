package com.samarina.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.samarina.model.Topic;
import com.samarina.model.User;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ServerApp {
    private static final Logger logger = LoggerFactory.getLogger(ServerApp.class);
    private static final int PORT = 8080; // Порт для подключения клиентов

    // Хранилище данных сервера
    private static final Map<String, Topic> topics = new HashMap<>(); // Все разделы с голосованиями
    private static final Set<User> activeUsers = new HashSet<>(); // Активные пользователи

    public static void main(String[] args) {
        logger.info("Запуск сервера...");

        // Группы потоков для обработки подключений
        EventLoopGroup bossGroup = new NioEventLoopGroup(); // Принимает новые подключения
        EventLoopGroup workerGroup = new NioEventLoopGroup(); // Обрабатывает подключения

        try {
            // Настройка сервера
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class) // Используем NIO транспорт
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // Настройка обработчиков для каждого подключения
                            ch.pipeline().addLast(
                                    new StringDecoder(), // Декодирование входящих строк
                                    new StringEncoder(), // Кодирование исходящих строк
                                    new ServerHandler()  // Наш обработчик команд
                            );
                            logger.info("Новое подключение: {}", ch.remoteAddress());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128) // Максимальная очередь подключений
                    .childOption(ChannelOption.SO_KEEPALIVE, true); // Поддержка соединения

            // Запуск сервера
            ChannelFuture future = bootstrap.bind(PORT).sync();
            logger.info("Сервер запущен на порту {}", PORT);
            System.out.println("Сервер запущен на порту " + PORT);

            // Ожидание завершения работы сервера
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error("Работа сервера была прервана: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        } finally {
            // Корректное завершение работы
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            logger.info("Сервер остановлен");
        }
    }

    /**
     * Авторизация пользователя
     * @param user объект пользователя
     * @return true если авторизация успешна, false если пользователь уже авторизован
     */
    public static synchronized boolean loginUser(User user) {
        if (activeUsers.contains(user)) {
            logger.warn("Попытка повторной авторизации пользователя: {}", user.getName());
            return false;
        }
        activeUsers.add(user);
        logger.info("Пользователь {} авторизован. Всего активных пользователей: {}",
                user.getName(), activeUsers.size());
        return true;
    }

    /**
     * Выход пользователя из системы
     * @param user объект пользователя
     */
    public static synchronized void logoutUser(User user) {
        activeUsers.remove(user);
        logger.info("Пользователь {} вышел из системы. Осталось активных пользователей: {}",
                user.getName(), activeUsers.size());
    }

    // Геттеры для доступа к данным сервера
    public static Map<String, Topic> getTopics() {
        return topics;
    }

    public static Set<User> getActiveUsers() {
        return activeUsers;
    }

    /**
     * Сохранение данных сервера в файл
     * @param filename имя файла для сохранения
     */
    public static void save(String filename) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT); // Красивый формат JSON

        try {
            // Создаем директорию data если ее нет
            File dataDir = new File("data");
            if (!dataDir.exists()) {
                dataDir.mkdir();
                logger.info("Создана директория для данных: {}", dataDir.getAbsolutePath());
            }

            // Сохраняем данные в файл
            File file = new File(dataDir, filename);
            mapper.writeValue(file, topics);
            logger.info("Данные успешно сохранены в файл: {}", file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Ошибка при сохранении данных в файл {}: {}", filename, e.getMessage(), e);
        }
    }

    /**
     * Загрузка данных сервера из файла
     * @param filename имя файла для загрузки
     */
    public static void load(String filename) {
        ObjectMapper mapper = new ObjectMapper();

        try {
            File dataDir = new File("data");
            if (!dataDir.exists()) {
                logger.error("Директория с данными не найдена: {}", dataDir.getAbsolutePath());
                throw new RuntimeException("Директория data не найдена");
            }

            File file = new File(dataDir, filename);
            if (!file.exists()) {
                logger.error("Файл с данными не найден: {}", file.getAbsolutePath());
                throw new RuntimeException("Файл " + filename + " не найден");
            }

            // Чтение данных из файла
            Map<String, Topic> loadedTopics = mapper.readValue(
                    file,
                    new TypeReference<Map<String, Topic>>() {}
            );

            // Очистка текущих данных и загрузка новых
            topics.clear();
            topics.putAll(loadedTopics);
            logger.info("Данные успешно загружены из файла: {}", file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Ошибка при загрузке данных из файла {}: {}", filename, e.getMessage(), e);
            throw new RuntimeException("Ошибка загрузки данных", e);
        }
    }
}
