package com.samarina.model;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class Topic {
    @Getter
    private String name;
    private Map<String, Vote> votes;

    public Topic(String name){
        this.name = name;
        this.votes = new HashMap<>();
    }

    public Vote getVote(String voteName) {
        return votes.get(voteName);
    }

    public Map<String, Vote> getAllVotes() {
        return votes;
    }

    public void addVote(Vote vote) {
        votes.put(vote.getName(), vote);
    }

    public void deleteVote(String voteName) {
        votes.remove(voteName);
    }
}