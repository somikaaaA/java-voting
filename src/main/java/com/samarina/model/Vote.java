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
    private User creator; // создатель голосования
    private Map<String, Integer> options; //варианты ответов и кол-во голосов

    public Vote(String name, String description, User creator) {
        this.name = name;
        this.description = description;
        this.creator = creator;
        this.options = new HashMap<>();
    }

    // проверка на создателя
    public boolean isCreator(User user) {
        return creator.equals(user);
    }

    public void addOption(String option) {
        options.put(option, 0); // изначально количество голосов для варианта равно 0
    }

    public void voteForOption(String option) {
        if (options.containsKey(option)) {
            options.put(option, options.get(option) + 1);
        }
    }

    public boolean removeOption(String option) {
        return options.remove(option) != null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Голосование: ").append(name).append("\n");
        sb.append("Описание: ").append(description).append("\n");
        sb.append("Варианты ответа:\n");
        for (Map.Entry<String, Integer> entry : options.entrySet()) {
            sb.append(" - ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" votes\n");
        }
        return sb.toString();
    }
}
