package com.samarina.model;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class Topic {
    @Getter
    private String name; // название
    private Map<String, Vote> votes; // голосования по разделу (ключ-голосование)

    public Topic(String name) {
        this.name = name;
        this.votes = new HashMap<>();
    }


}
