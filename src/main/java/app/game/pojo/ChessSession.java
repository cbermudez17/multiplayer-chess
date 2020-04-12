package app.game.pojo;

import org.json.JSONObject;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private void removePlayer(WebSocketSession session) throws Exception {
        // Close the WebSocketSession
        session.close();

        // Check if there remains another player
        if (this.isFull) {
            // Remove the player from the ChessSession
            String name = this.playerSessions.remove(session);
            this.isFull = false;

            // Send a disconnect message to the remaining player
            JSONObject message = new JSONObject();
            message.put("sender", "");
            message.put("message", String.format("%s has been disconnected", name));
            sendMessage(message.toString());
        }
    }

    public void sendMove(WebSocketSession fromSession, String move) {
        for (WebSocketSession session : this.playerSessions.keySet()) {
            if (session == fromSession) {
                continue;
            }
            try {
                sendMessage(session, move);
            } catch (IOException e) {
                try {
                    // If message fails, remove the player from the session
                    removePlayer(session);
                } catch (Exception e1) {}
            }
        }
    }

    public void sendMessage(String message) {
        // Store the message in the chat history
        messages.add(message);

        // Send the message to every player
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : this.playerSessions.keySet()) {
            try {
                session.sendMessage(textMessage);
            } catch (IOException e) {
                try {
                    // If message fails, remove the player from the session
                    removePlayer(session);
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