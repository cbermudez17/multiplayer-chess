package app.game.pojo;

import org.json.JSONObject;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

public class ChessSession {
    private Map<WebSocketSession, String> playerSessions = new HashMap<>();
    private List<String> messages = new ArrayList<>();
    private boolean isFull;

    public ChessSession(WebSocketSession session, String name) {
        this.playerSessions.put(session, name);
        this.isFull = false;
    }

    public void addPlayer(WebSocketSession session, String name) throws Exception {
        // Make sure the ChessSession is not already full
        if (this.isFull) {
            throw new Exception("Cannot add a player to a full chess session");
        } else {
            this.isFull = true;
            this.playerSessions.put(session, name);

            // Send all stored messages to the new player's chat
            for (String message : this.messages) {
                sendMessage(session, message);
            }
        }
    }

    private void removePlayer(Entry<WebSocketSession, String> entry, Iterator<Entry<WebSocketSession, String>> iterator)
            throws IOException {
        WebSocketSession session = entry.getKey();
        String name = entry.getValue();

        // Close the WebSocketSession
        session.close();

        // Check if there remains another player
        if (this.isFull) {
            // Remove the player from the ChessSession
            iterator.remove();
            this.isFull = false;

            // Send a disconnect message to the remaining player
            JSONObject message = new JSONObject();
            message.put("sender", "");
            message.put("message", String.format("%s has been disconnected", name));
            sendMessage(message.toString());
        }
    }

    public void sendMove(WebSocketSession fromSession, String move) {
        Iterator<Entry<WebSocketSession, String>> iterator = this.playerSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<WebSocketSession, String> entry = iterator.next();
            WebSocketSession session = entry.getKey();
            if (session == fromSession) {
                continue;
            }
            try {
                sendMessage(session, move);
            } catch (IOException | IllegalStateException e) {
                try {
                    // If message fails, remove the player from the session
                    removePlayer(entry, iterator);
                } catch (Exception e1) {}
            }
        }
    }

    public void sendMessage(String message) {
        // Store the message in the chat history
        messages.add(message);

        // Send the message to every player
        TextMessage textMessage = new TextMessage(message);

        // Using an iterator will prevent any ConcurrentModificationException
        Iterator<Entry<WebSocketSession, String>> iterator = this.playerSessions.entrySet().iterator();
        Entry<WebSocketSession, String> entry = null;
        while (iterator.hasNext()) {
            try {
                entry = iterator.next();
                entry.getKey().sendMessage(textMessage);
            } catch (IOException | IllegalStateException e) {
                try {
                    // If message fails, remove the player from the session
                    removePlayer(entry, iterator);
                } catch (Exception e1) {}
            }
        }
    }

    private void sendMessage(WebSocketSession session, String message) throws IOException {
        session.sendMessage(new TextMessage(message));
    }

    public boolean isFull() {
        return isFull;
    }
}