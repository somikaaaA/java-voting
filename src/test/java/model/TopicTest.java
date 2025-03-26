package model;

import com.samarina.model.Topic;
import com.samarina.model.Vote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TopicTest {
    private Topic topic;
    private Vote vote;

    @BeforeEach
    void setUp() {
        topic = new Topic("testTopic");
        Map<String, List<String>> options = new HashMap<>();
        vote = new Vote("testVote", "Test description", options, "creator");
    }

    @Test
    void testTopicCreation() {
        assertEquals("testTopic", topic.getName());
        assertTrue(topic.getAllVotes().isEmpty());
    }

    @Test
    void testAddVote() {
        topic.addVote(vote);
        assertEquals(1, topic.getAllVotes().size());
        assertSame(vote, topic.getVote("testVote"));
    }

    @Test
    void testDeleteVote() {
        topic.addVote(vote);
        topic.deleteVote("testVote");
        assertNull(topic.getVote("testVote"));
    }

    @Test
    void testGetNonExistentVote() {
        assertNull(topic.getVote("nonexistent"));
    }
}
