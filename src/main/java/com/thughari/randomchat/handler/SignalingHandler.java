package com.thughari.randomchat.handler;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class SignalingHandler extends TextWebSocketHandler {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Queue<String> waitingUsers = new LinkedList<>();
    private final Map<String, String> peerMap = new HashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);

        synchronized (waitingUsers) {
            if (waitingUsers.isEmpty()) {
                waitingUsers.add(session.getId());
            } else {
                String peerId = waitingUsers.poll();
                peerMap.put(session.getId(), peerId);
                peerMap.put(peerId, session.getId());

                sessions.get(session.getId()).sendMessage(new TextMessage("{\"type\": \"ready\"}"));
                sessions.get(peerId).sendMessage(new TextMessage("{\"type\": \"ready\"}"));
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String peerId = peerMap.get(session.getId());
        if (peerId != null && sessions.containsKey(peerId)) {
            sessions.get(peerId).sendMessage(message);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        waitingUsers.remove(session.getId());
        String peerId = peerMap.remove(session.getId());
        if (peerId != null) {
            peerMap.remove(peerId);
            WebSocketSession peerSession = sessions.get(peerId);
            if (peerSession != null && peerSession.isOpen()) {
                peerSession.sendMessage(new TextMessage("{\"type\": \"leave\"}"));
            }
        }
    }
}
