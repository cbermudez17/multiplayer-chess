package app.game.config;

import app.game.pojo.ChessSession;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SocketTextHandler extends TextWebSocketHandler {

    private static List<ChessSession> sessions = new ArrayList<>();
    private static final Map<WebSocketSession, ChessSession> sessionMap = new HashMap<>();

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        String payload = message.getPayload();
        JSONObject data = new JSONObject(payload);
        String type = data.getString("type");
        switch (type) {
            case "init":
                handleInitialization(session, data);
                break;
            case "chat":
                handleChatAction(session, data);
                break;
            case "game":
                handleGameAction(session, data);
                break;
            default:
                System.out.println(String.format("%s is not a valid message type", type));
        }
    }

    private void handleInitialization(WebSocketSession socketSession, JSONObject data) throws Exception {
        String name = data.getString("name");

        // Find next open ChessSession
        ChessSession openChessSession = null;
        for (ChessSession chessSession : this.sessionMap.values()) {
            // If open ChessSession found, add new player
            if (!chessSession.isFull()) {
                chessSession.addPlayer(socketSession, name);
                openChessSession = chessSession;
                break;
            }
        }

        // If no open ChessSession is found, start a new ChessSession
        if (openChessSession == null) {
            openChessSession = new ChessSession(socketSession, name);
        }

        // Store the socket/chess session pair
        this.sessionMap.put(socketSession, openChessSession);

        // Send a connection message to the ChessSession
        JSONObject message = new JSONObject();
        message.put("sender", "");
        message.put("message", String.format("%s has joined", name));
        openChessSession.sendMessage(message.toString());
    }

    private void handleGameAction(WebSocketSession socketSession, JSONObject data) {
        this.sessionMap.get(socketSession).sendMove(socketSession, data.toString());
    }

    private void handleChatAction(WebSocketSession socketSession, JSONObject data) throws Exception {
        this.sessionMap.get(socketSession).sendMessage(data.toString());
    }

//    private void sendMessageToAll(String message) throws IOException {
//        TextMessage msg = new TextMessage(message);
//        for (ChessSession session : sessions) {
//            try {
//                session.getSession().sendMessage(msg);
//            } catch (IOException e) {
//                sessions.remove(session);
//                try {
//                    session.getSession().close();
//                } catch (IOException e1) {}
//                JSONObject obj = new JSONObject();
//                String disconnectMsg = String.format("%s %s", session.getName(), "has been disconnected");
//                obj.put("sender", "");
//                obj.put("message", disconnectMsg);
//                sendMessageToAll(obj.toString());
//            }
//        }
//    }

//    private void sendMessageToOthers(WebSocketSession userSession, String message) throws IOException {
//        TextMessage msg = new TextMessage(message);
//        for (ChessSession session : sessions) {
//            if (session.getSession() == userSession) {
//                continue;
//            }
//            try {
//                session.getSession().sendMessage(msg);
//            } catch (IOException e) {
//                sessions.remove(session);
//                try {
//                    session.getSession().close();
//                } catch (IOException e1) {}
//                JSONObject obj = new JSONObject();
//                String disconnectMsg = String.format("%s %s", session.getName(), "has been disconnected");
//                obj.put("sender", "");
//                obj.put("message", disconnectMsg);
//                sendMessageToAll(obj.toString());
//            }
//        }
//    }

//    private void removeConnection(ChessSession session) {
//        JSONObject obj = new JSONObject();
//        String msg = String.format("%s %s", session.getName(), "has been disconnected");
//        obj.put("sender", "");
//        obj.put("message", msg);
//        try {
//            sendMessageToAll(obj.toString());
//        } catch (IOException e) {}
//    }

}