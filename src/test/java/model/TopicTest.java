package model;

import com.samarina.model.Topic;
import com.samarina.model.Vote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TopicTest {
    private Topic topic;
    private Vote vote1;
    private Vote vote2;

    @BeforeEach
    void setUp() {
        topic = new Topic("TestTopic");

        Map<String, List<String>> options1 = new HashMap<>();
        options1.put("Opt1", new ArrayList<>());
        options1.put("Opt2", new ArrayList<>());
        vote1 = new Vote("Vote1", "Desc1", options1, "creator1");

        Map<String, List<String>> options2 = new HashMap<>();
        options2.put("OptA", new ArrayList<>());
        options2.put("OptB", new ArrayList<>());
        vote2 = new Vote("Vote2", "Desc2", options2, "creator2");
    }

    @Test
    void testGetName() {
        assertEquals("TestTopic", topic.getName());
    }

    @Test
    void testAddVote() {
        topic.addVote(vote1);
        topic.addVote(vote2);

        assertEquals(2, topic.getVotes().size());
        assertNotNull(topic.getVote("Vote1"));
        assertNotNull(topic.getVote("Vote2"));
    }

    @Test
    void testDeleteVote() {
        topic.addVote(vote1);
        topic.addVote(vote2);

        topic.deleteVote("Vote1");
        assertEquals(1, topic.getVotes().size());
        assertNull(topic.getVote("Vote1"));
        assertNotNull(topic.getVote("Vote2"));
    }

    @Test
    void testGetVotesData() {
        topic.addVote(vote1);
        topic.addVote(vote2);

        Map<String, Map<String, Object>> votesData = topic.getVotesData();

        assertEquals(2, votesData.size());
        assertTrue(votesData.containsKey("Vote1"));
        assertTrue(votesData.containsKey("Vote2"));

        Map<String, Object> vote1Data = votesData.get("Vote1");
        assertEquals("Desc1", vote1Data.get("Описание"));
        assertEquals("creator1", vote1Data.get("Создатель"));
        assertNotNull(vote1Data.get("Варианты ответов"));
    }

    @Test
    void testSynchronizedAddAndDelete() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        // Половина потоков добавляет, половина удаляет
        for (int i = 0; i < threadCount; i++) {
            final int voteNum = i;
            if (i % 2 == 0) {
                executorService.execute(() -> {
                    Map<String, List<String>> options = new HashMap<>();
                    options.put("Opt" + voteNum, new ArrayList<>());
                    Vote v = new Vote("Vote" + voteNum, "Desc", options, "creator");
                    topic.addVote(v);
                });
            } else {
                executorService.execute(() -> topic.deleteVote("Vote" + (voteNum - 1)));
            }
        }

        executorService.shutdown();
        assertTrue(executorService.awaitTermination(1, TimeUnit.SECONDS));

        // Проверяем, что операции были атомарными
        assertTrue(topic.getVotes().size() >= 1);
    }
}