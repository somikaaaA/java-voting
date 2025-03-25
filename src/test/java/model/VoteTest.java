package model;

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

class VoteTest {
    private Vote vote;
    private Map<String, List<String>> options;

    @BeforeEach
    void setUp() {
        options = new HashMap<>();
        options.put("Option1", new ArrayList<>());
        options.put("Option2", new ArrayList<>());
        vote = new Vote("TestVote", "Test Description", options, "creator1");
    }

    @Test
    void testGetName() {
        assertEquals("TestVote", vote.getName());
    }

    @Test
    void testGetDescription() {
        assertEquals("Test Description", vote.getDescription());
    }

    @Test
    void testGetCreator() {
        assertEquals("creator1", vote.getCreator());
    }

    @Test
    void testGetOptions() {
        Map<String, List<String>> retrievedOptions = vote.getOptions();
        assertEquals(2, retrievedOptions.size());
        assertTrue(retrievedOptions.containsKey("Option1"));
        assertTrue(retrievedOptions.containsKey("Option2"));
    }

    @Test
    void testAddVote() {
        vote.addVote("Option1", "user1");
        vote.addVote("Option1", "user2");
        vote.addVote("Option2", "user3");

        Map<String, List<String>> options = vote.getOptions();
        assertEquals(2, options.get("Option1").size());
        assertEquals(1, options.get("Option2").size());
        assertTrue(options.get("Option1").contains("user1"));
        assertTrue(options.get("Option1").contains("user2"));
        assertTrue(options.get("Option2").contains("user3"));
    }

    @Test
    void testAddVoteToNonExistingOption() {
        vote.addVote("Option3", "user1");
        assertTrue(vote.getOptions().containsKey("Option3"));
        assertEquals(1, vote.getOptions().get("Option3").size());
    }

    @Test
    void testSynchronizedAddVote() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int userId = i;
            executorService.execute(() -> vote.addVote("Option1", "user" + userId));
        }

        executorService.shutdown();
        assertTrue(executorService.awaitTermination(1, TimeUnit.SECONDS));

        assertEquals(threadCount, vote.getOptions().get("Option1").size());
    }
}