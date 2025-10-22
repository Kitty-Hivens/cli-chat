package goldyl.chat;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static final int PORT = 8080;
    // Используем Map для хранения "имя -> поток_для_записи"
    private static final Map<String, PrintWriter> clients = new ConcurrentHashMap<>();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Стандартная точка входа в приложение
    static void main() {
        startServer();
    }

    public static void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                // Запускаем обработку клиента в виртуальном потоке
                Thread.startVirtualThread(() -> handleClient(socket));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void handleClient(Socket socket) {
        String name = null;
        PrintWriter writer;

        try (socket; // Ресурс сокета будет закрыт автоматически
             var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             // writer не объявляем здесь, чтобы управлять им в finally
             var tempWriter = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)
        ) {
            writer = tempWriter; // Присваиваем writer'a переменной из внешней области видимости

            // 1. Цикл регистрации имени
            while (true) {
                writer.println("Введите ваше имя:");
                name = reader.readLine();

                if (name == null) {
                    return; // Клиент отключился, не введя имя
                }
                if (name.isBlank() || name.contains(" ")) {
                    writer.println("--- Имя не может быть пустым или содержать пробелы. ---");
                    continue;
                }
                // putIfAbsent атомарно добавляет, если ключа нет, и возвращает null.
                // Если ключ есть, он возвращает существующее значение (не null).
                if (clients.putIfAbsent(name, writer) == null) {
                    writer.println("--- Добро пожаловать в чат, " + name + "! ---");
                    writer.println("--- Для личного сообщения введите: /msg <имя_получателя> <сообщение> ---");
                    break; // Имя успешно зарегистрировано
                } else {
                    writer.println("--- Это имя уже занято. Попробуйте другое. ---");
                }
            }

            // 2. Оповещаем всех о новом участнике
            System.out.println(name + " присоединился к чату.");
            broadcast("--- " + name + " присоединился к чату. ---");

            // 3. Цикл обработки сообщений
            String message;
            while ((message = reader.readLine()) != null) {
                if (message.isBlank()) continue;

                if (message.startsWith("/msg ")) {
                    // Это личное сообщение
                    handlePrivateMessage(name, message);
                } else {
                    // Это публичное сообщение
                    String timestamp = LocalDateTime.now().format(formatter);
                    String formattedMessage = String.format("[%s] [%s]: %s", timestamp, name, message);
                    System.out.println("Get " + formattedMessage);
                    broadcast(formattedMessage);
                }
            }
        } catch (IOException e) {
            // Чаще всего это происходит, когда клиент разрывает соединение
            System.out.println("Клиент " + (name != null ? name : "") + " отсоединился с ошибкой: " + e.getMessage());
        } finally {
            // 4. Очистка при выходе
            if (name != null) {
                clients.remove(name);
                System.out.println(name + " покинул чат.");
                broadcast("--- " + name + " покинул чат. ---");
            }
        }
    }

    /**
     * Обрабатывает личное сообщение.
     * @param senderName Имя отправителя
     * @param fullMessage Полная строка, начиная с /msg
     */
    private static void handlePrivateMessage(String senderName, String fullMessage) {
        // Формат: /msg <targetName> <message>
        String[] parts = fullMessage.split(" ", 3);

        if (parts.length < 3) {
            PrintWriter senderWriter = clients.get(senderName);
            if (senderWriter != null) {
                senderWriter.println("--- Ошибка: неверный формат. Используйте: /msg <имя> <сообщение> ---");
            }
            return;
        }

        String targetName = parts[1];
        String message = parts[2];
        String timestamp = LocalDateTime.now().format(formatter);

        PrintWriter targetWriter = clients.get(targetName);
        PrintWriter senderWriter = clients.get(senderName);

        if (targetWriter == null) {
            if (senderWriter != null) {
                senderWriter.println("--- Ошибка: пользователь '" + targetName + "' не найден. ---");
            }
            return;
        }

        if (targetName.equals(senderName)) {
            if (senderWriter != null) {
                senderWriter.println("--- Нельзя отправить личное сообщение самому себе. ---");
            }
            return;
        }

        // Отправляем сообщение получателю
        String formattedPM = String.format("[%s] [ЛС от %s]: %s", timestamp, senderName, message);
        targetWriter.println(formattedPM);

        // Отправляем подтверждение (эхо) отправителю
        if (senderWriter != null) {
            String formattedEcho = String.format("[%s] [ЛС для %s]: %s", timestamp, targetName, message);
            senderWriter.println(formattedEcho);
        }
    }

    /**
     * Отправляет сообщение всем подключенным клиентам.
     * @param message Сообщение для трансляции
     */
    private static void broadcast(String message) {
        for (PrintWriter client : clients.values()) {
            client.println(message);
        }
    }
}