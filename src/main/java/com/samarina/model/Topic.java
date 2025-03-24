package com.samarina.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Topic {
    @Getter
    private String name; // название
    private Map<String, Vote> votes; // голосования по разделу (ключ-голосование)

    public Topic(String name) {
        this.name = name;
        this.votes = new HashMap<>();
    }

    public void addVote(Vote vote) {
        votes.put(vote.getName(), vote);
    }

    public Vote getVote(String voteName) {
        return votes.get(voteName);
    }

    public List<Vote> getVotes() {
        return new ArrayList<>(votes.values());
    }

    public boolean removeVote(String voteName, User user) {
        Vote vote = votes.get(voteName);
        if (vote != null && vote.isCreator(user)) {
            votes.remove(voteName);
            return true;
        }
        return false;
    }

    public int getVotesCount() {
        return votes.size();
    }

    @Override
    public String toString() {
        return name + " (голосований в разделе =" + getVotesCount() + ")";
    }
}
