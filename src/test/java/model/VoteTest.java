package model;

import com.samarina.model.Vote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class VoteTest {
    private Vote vote;

    @BeforeEach
    void setUp() {
        Map<String, List<String>> options = new HashMap<>();
        options.put("option1", List.of());
        options.put("option2", List.of());
        vote = new Vote("testVote", "Test description", options, "creator");
    }

    @Test
    void testVoteCreation() {
        assertEquals("testVote", vote.getName());
        assertEquals("Test description", vote.getDescription());
        assertEquals("creator", vote.getCreator());
        assertEquals(2, vote.getOptions().size());
    }
}
