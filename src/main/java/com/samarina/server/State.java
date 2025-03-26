package com.samarina.server;

public enum State {
    MENU, // начальное состояние - ожидание меню
    WAITING_FOR_NAME, //ожидание названия
    WAITING_FOR_DESC, //описания
    WAITING_FOR_QUANTITY,//кол-ва вариантов ответа
    WAITING_FOR_OPTIONS,//вариантов ответа
    WAITING_FOR_VOTE//выбора варианта голосования
}
