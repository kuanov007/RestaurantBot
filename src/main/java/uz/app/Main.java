package uz.app;

import com.github.javafaker.Faker;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import uz.app.service.MyBot;
import uz.app.utils.UTIL;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class Main {
    private static final ResourceBundle resourceBundle = ResourceBundle.getBundle("config");
    private static final Faker faker = new Faker();

    static {
        for (String[] menu : UTIL.menu) {
            for (String dish : menu) {
                int amount = faker.number().numberBetween(1, 10);
                List<String> strings = new ArrayList<>();
                for (int i = 0; i < amount; i++) {
                    strings.add(faker.food().dish());
                }
                strings.add("back");
                UTIL.menuMap.put(dish, strings);
            }
        }
    }

    public static void main(String[] args) throws TelegramApiException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        telegramBotsApi.registerBot(new MyBot(resourceBundle.getString("bot.token")));
    }
}