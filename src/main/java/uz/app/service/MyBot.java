package uz.app.service;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.app.constant.State;
import uz.app.entity.Food;
import uz.app.utils.UTIL;

import java.math.BigDecimal;
import java.sql.SQLOutput;
import java.util.*;

public class MyBot extends TelegramLongPollingBot {

    public MyBot(String botToken) {
        super(botToken);
    }

    private static final Map<Long, State> userStates = new HashMap<>();

    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        if (message != null) {
            if (message.hasContact()) {
                userStates.put(message.getChatId(), State.MAIN_MENU);
            } else if (message.hasText()) {
                if (!isAnyButtonClicked(message)) {
                    isAnyTextThere(message);
                }
            } else {
                iDontUnderstandYou(message.getChatId());
            }
            requestPart(message);

        }

    }

    private void requestPart(Message message) {
        Long chatId = message.getChatId();
        switch (userStates.get(chatId)) {
            case ENTER_CONTACT -> {
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(chatId);
                sendMessage.setText("Hello, Welcome to my restaurant\nPlease, share your contact");

                ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
                replyKeyboardMarkup.setResizeKeyboard(true);
                KeyboardButton keyboardButton = new KeyboardButton("Share contact");
                keyboardButton.setRequestContact(true);
                replyKeyboardMarkup.setKeyboard(List.of(new KeyboardRow() {{
                    add(keyboardButton);
                }}));
                sendMessage.setReplyMarkup(replyKeyboardMarkup);
                executeExecuteMessage(sendMessage);
            }
            case MAIN_MENU -> {
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(chatId);
                sendMessage.setText("Main menu");
                addButtons(sendMessage, UTIL.mainMenu);
                executeExecuteMessage(sendMessage);
            }
            case MENU -> {
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(chatId);
                sendMessage.setText("Menu");
                addButtons(sendMessage, UTIL.menu);
                executeExecuteMessage(sendMessage);
            }
            case BASKET -> {
                if (UTIL.basketMap.get(chatId) == null || UTIL.basketMap.get(chatId).isEmpty()) {
                    SendMessage sendMessage = new SendMessage();
                    sendMessage.setChatId(chatId);
                    sendMessage.setText("Your basket is empty");
                    executeExecuteMessage(sendMessage);
                } else {
                    double totalPrice = UTIL.basketMap.get(chatId).stream().mapToDouble(food -> food.getPrice() * food.getAmount()).sum();
                    String basketInfo = "Your basket:\n" +
                                        "Total foods : " + UTIL.basketMap.get(chatId).stream().mapToInt(Food::getAmount).sum() + "\n" +
                                        "Total order : " + UTIL.basketMap.get(chatId).size() + "\n" +
                                        "Total dishes: " + UTIL.basketMap.get(chatId).stream().map(Food::getDish).distinct().count() + "\n" +
                                        "Total price: " + Math.floor(totalPrice*100)/100;

                    SendMessage sendMessage = new SendMessage();
                    sendMessage.setChatId(chatId);
                    sendMessage.setText(basketInfo);
                    executeExecuteMessage(sendMessage);
                    System.out.println(UTIL.basketMap.get(chatId));
                }

            }
            case SELECT_FOOD -> {
                List<String> strings = UTIL.menuMap.get(UTIL.foodMap.get(chatId).getDish());
                SendMessage sendMessage = generateDishOptionsMessage(strings, chatId);
                executeExecuteMessage(sendMessage);
            }
            case ENTER_AMOUNT -> {
                Food food = UTIL.foodMap.get(chatId);
                String photoUri = UTIL.photos.get(food.getDish()).get(new Random().nextInt(0, UTIL.photos.get(food.getDish()).size() - 1));
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(chatId);
                sendPhoto.setPhoto(new InputFile(photoUri));
                sendPhoto.setCaption("""
                        Food name: %s
                        Food dish: %s
                        Food price: %s $
                        
                        How many want to order? Enter it:
                        """.formatted(food.getName(), food.getDish(), food.getPrice()));

                ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
                replyKeyboardMarkup.setResizeKeyboard(true);
                KeyboardButton keyboardButton = new KeyboardButton("back");
                KeyboardRow keyboardRow = new KeyboardRow();
                keyboardRow.add(keyboardButton);
                replyKeyboardMarkup.setKeyboard(List.of(keyboardRow));

                sendPhoto.setReplyMarkup(replyKeyboardMarkup);
                try {
                    execute(sendPhoto);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }

            }
        }
    }

    private void isAnyTextThere(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText();
        switch (userStates.get(chatId)) {
            case ENTER_CONTACT, MAIN_MENU, MENU, BASKET, SELECT_FOOD -> {
                iDontUnderstandYou(chatId);
            }
            case ENTER_AMOUNT -> {
                if (text.matches("\\d+")) {
                    try {
                        int amount = Integer.parseInt(text);
                        if (amount < 1) {
                            return;
                        }
                        UTIL.foodMap.get(chatId).setAmount(amount);

                        SendMessage sendMessage = new SendMessage();
                        sendMessage.setChatId(chatId);
                        sendMessage.setText("Your order added to your basket!!!");
                        executeExecuteMessage(sendMessage);
                        UTIL.basketMap.computeIfAbsent(chatId, k -> new ArrayList<>()).add(UTIL.foodMap.get(chatId));
                        UTIL.foodMap.remove(chatId);
                        userStates.put(chatId, State.MAIN_MENU);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

    }

    private boolean isAnyButtonClicked(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText();
        if (text.equals("/start")) {
            whenStartSend(chatId);
            return true;
        } else {
            Optional<String> mainMenuIfExist = getMainMenuIfExist(text);
            if (mainMenuIfExist.isPresent()) {
                String selectedMenu = mainMenuIfExist.get();
                switch (selectedMenu) {
                    case "menu" -> {
                        userStates.put(chatId, State.MENU);
                    }
                    case "basket" -> {
                        userStates.put(chatId, State.BASKET);
                    }
                    case "contact to admin" -> {
                        SendMessage sendMessage = new SendMessage();
                        sendMessage.setChatId(chatId);
                        sendMessage.setText("Contact: +998900000000");
                        executeExecuteMessage(sendMessage);
                        userStates.put(chatId, State.MAIN_MENU);
                    }
                }
                return true;
            } else {
                Optional<String> menuIfExist = getMenuIfExist(text);
                if (menuIfExist.isPresent()) {
                    String selectedMenu = menuIfExist.get();
                    if (selectedMenu.equals("back")) {
                        if (userStates.get(chatId) == State.SELECT_FOOD) {
                            userStates.put(chatId, State.MENU);
                        } else if (userStates.get(chatId) == State.ENTER_AMOUNT) {
                            userStates.put(chatId, State.SELECT_FOOD);
                        } else if (userStates.get(chatId) == State.MENU) {
                            userStates.put(chatId, State.MAIN_MENU);
                        }
                        return true;
                    }

                    UTIL.foodMap.put(chatId, Food.builder().dish(selectedMenu).build());
                    List<String> strings = UTIL.menuMap.get(selectedMenu);
                    if (strings == null || strings.isEmpty()) {
                        SendMessage sendMessage = new SendMessage();
                        sendMessage.setChatId(chatId);
                        sendMessage.setText("Sorry, but we don't have this dish");
                        executeExecuteMessage(sendMessage);
                    } else {
                        userStates.put(chatId, State.SELECT_FOOD);
                    }
                    return true;
                } else {
                    Optional<String> dishIfExist = getDishIfExist(text);
                    if (dishIfExist.isPresent()) {
                        userStates.put(chatId, State.ENTER_AMOUNT);
                        UTIL.foodMap.get(chatId).setName(dishIfExist.get());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static SendMessage generateDishOptionsMessage(List<String> strings, Long chatId) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setResizeKeyboard(true);

        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        for (int i = 0; i < strings.size() - 1; i++) {
            if ((i + 1) % 3 == 0) {
                row.add(strings.get(i));
                keyboardRows.add(row);
                row = new KeyboardRow();
                continue;
            }
            row.add(strings.get(i));
        }
        if (!row.isEmpty()) {
            keyboardRows.add(row);
        }
        row = new KeyboardRow();
        row.add(strings.get(strings.size() - 1));

        keyboardRows.add(row);
        replyKeyboardMarkup.setKeyboard(keyboardRows);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("Please, select your dish");
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        return sendMessage;
    }

    private void executeExecuteMessage(SendMessage sendMessage) {
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<String> getMainMenuIfExist(String mainMenuName) {
        for (String[] mainMenu : UTIL.mainMenu) {
            for (String menu : mainMenu) {
                if (menu.equals(mainMenuName)) {
                    return Optional.of(mainMenuName);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> getMenuIfExist(String menuName) {
        for (String[] menu : UTIL.menu) {
            for (String s : menu) {
                if (s.equals(menuName)) {
                    return Optional.of(menuName);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> getDishIfExist(String dishName) {
        Collection<List<String>> values = UTIL.menuMap.values();
        if (values.stream()
                .flatMap(Collection::stream)
                .anyMatch(dish -> dish.equals(dishName))) {
            return Optional.of(dishName);
        }
        return Optional.empty();
    }


    public static void addButtons(SendMessage sendMessage, String[][] buttons) {
        ReplyKeyboardMarkup replyKeyboard = new ReplyKeyboardMarkup();
        replyKeyboard.setResizeKeyboard(true);
        List<KeyboardRow> keyboardRows = new ArrayList<>();

        for (String[] button : buttons) {
            KeyboardRow row = new KeyboardRow();
            for (String s : button) {
                row.add(s);
            }
            keyboardRows.add(row);
        }
        replyKeyboard.setKeyboard(keyboardRows);
        sendMessage.setReplyMarkup(replyKeyboard);
    }

    private void whenStartSend(Long chatId) {
        State state = userStates.get(chatId);
        if (state == null) {
            userStates.put(chatId, State.ENTER_CONTACT);
        } else {

            userStates.put(chatId, State.MAIN_MENU);
        }
    }

    private void iDontUnderstandYou(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("I don't understand you");
        executeExecuteMessage(sendMessage);
    }

    public String getBotUsername() {
        return "G54_AK_bot";
    }
}
