package goldyl.chat;

import java.io.*;
import java.net.Socket;

public class ChatClient {

    // Стандартная точка входа в приложение
    static void main() {
        try (var socket = new Socket("localhost", 8080);
             var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             var writer = new PrintWriter(socket.getOutputStream(), true);
             var console = new BufferedReader(new InputStreamReader(System.in))) {

            Thread.startVirtualThread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    // Сервер отключился или соединение разорвано
                    System.out.println("--- Соединение с сервером потеряно. ---");
                    System.exit(0); // Завершаем работу клиента
                }
            });

            // Основной поток используется для отправки сообщений
            // Первое, что напечатает пользователь, будет его имя
            String input;
            while ((input = console.readLine()) != null) {
                writer.println(input); // Просто отправляем "сырой" ввод на сервер
            }

        } catch (IOException e) {
            System.err.println("Не удалось подключиться к серверу: " + e.getMessage());
            // throw new RuntimeException(e); // Можно просто завершить программу
        }
    }
}