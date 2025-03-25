package model;

import com.samarina.model.Vote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VoteTest {
    private Vote vote;
    private Map<String, List<String>> options;

    @BeforeEach
    void setUp() {
        options = new HashMap<>();
        options.put("Python", new ArrayList<>());
        options.put("Java", new ArrayList<>());
        vote = new Vote("Best Language", "Vote for best language", options, "admin");
    }

    @Test
    void shouldAddVote() {
        vote.addVote("Python", "user1");
        assertEquals(1, vote.getOptions().get("Python").size());
        assertTrue(vote.getOptions().get("Python").contains("user1"));
    }
}
