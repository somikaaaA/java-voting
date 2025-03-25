package com.samarina.model;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class Topic {
    private String name;
    private Map<String, Vote> votes;

    public Topic(String name){
        this.name = name;
        this.votes = new HashMap<>();
    }

    public Vote getVote(String voteName) {
        return votes.get(voteName);
    }

    public synchronized void addVote(Vote vote) {
        votes.put(vote.getName(), vote);
    }

    public synchronized void deleteVote(String voteName) {
        votes.remove(voteName);
    }

    public Map<String, Map<String, Object>> getVotesData() {
        Map<String, Map<String, Object>> votesData = new HashMap<>();
        votes.forEach((name, vote) -> {
            Map<String, Object> voteData = new HashMap<>();
            voteData.put("Описание", vote.getDescription());
            voteData.put("Создатель", vote.getCreator());
            voteData.put("Варианты ответов", vote.getOptions());
            votesData.put(name, voteData);
        });
        return votesData;
    }
}