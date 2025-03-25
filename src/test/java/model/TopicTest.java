package model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.samarina.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class TopicTest {
    private Topic topic;
    private Vote vote;

    @BeforeEach
    void setUp() {
        topic = new Topic("Programming");
        Map<String, List<String>> options = new HashMap<>();
        options.put("Python", new ArrayList<>());
        vote = new Vote("Best Language", "Vote for best language", options, "admin");
    }

    @Test
    void shouldAddVote() {
        topic.addVote(vote);
        assertEquals(1, topic.getVotes().size());
        assertEquals(vote, topic.getVote("Best Language"));
    }

    @Test
    void shouldDeleteVote() {
        topic.addVote(vote);
        topic.deleteVote("Best Language");
        assertEquals(0, topic.getVotes().size());
    }

    @Test
    void shouldReturnVotesData() {
        topic.addVote(vote);
        Map<String, Map<String, Object>> votesData = topic.getVotesData();
        assertEquals(1, votesData.size());
        assertTrue(votesData.containsKey("Best Language"));
    }
}
