package com.samarina.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Vote {
    @Getter
    private String name;
    @Getter
    private String description;
    @Getter
    private String creator;
    private Map<String, List<String>> options;

    public Vote(String name, String description, Map<String, List<String>> options, String creator) {
        this.name = name;
        this.description = description;
        this.options = new HashMap<>(options);
        this.creator = creator;
    }

    public void vote(String option, String voter) {
        if (options == null) {
            options = new HashMap<>();
        }
        options.computeIfAbsent(option, k -> new ArrayList<>()).add(voter);
    }

    public Map<String, List<String>> getOptions(){
        if(options == null){
            options = new HashMap<>();
        }
        return options;
    }
}