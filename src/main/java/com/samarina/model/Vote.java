package com.samarina.model;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class Vote {
    private String name; // название
    private String description; // описание
    private Map<String, Integer> options; //варианты ответов и кол-во голосов

    public Vote(String name, String description) {
        this.name = name;
        this.description = description;
        this.options = new HashMap<>();
    }
}
