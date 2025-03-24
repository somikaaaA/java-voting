package com.samarina.model;

import java.util.Map;

public class Topic {
    private String name; // название
    private Map<String, Vote> votes; // голосования по разделу (ключ-голосование)
}
