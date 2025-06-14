package com.thughari.randomchat.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

@Component
public class SignalingHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(SignalingHandler.class);
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Queue<String> waitingUsers = new ConcurrentLinkedQueue<>();
    private final Map<String, String> peerMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    @Qualifier("virtualThreadTaskExecutor")
    private ExecutorService virtualThreadExecutor;

    private volatile boolean isShuttingDown = false;

    @PreDestroy
    public void shutdown() {
        isShuttingDown = true;
        virtualThreadExecutor.shutdown();
        try {
            if (!virtualThreadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                virtualThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            virtualThreadExecutor.shutdownNow();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        executeIfNotShutdown(() -> {
            sessions.put(session.getId(), session);
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        executeIfNotShutdown(() -> {
            try {
                Map<String, Object> messageData = objectMapper.readValue(message.getPayload(), new TypeReference<>() {});
                String messageType = (String) messageData.get("type");
                String sessionId = session.getId();

                switch (messageType) {
                    case "ready_for_peer" -> {
                        tryToPairUser(sessionId);
                    }
                    case "leave" -> {
                        handlePeerDisconnection(sessionId);
                    }
                    case "offer", "answer", "ice", "media_status" -> {
                        String peerId = peerMap.get(sessionId);
                        if (peerId != null) {
                            WebSocketSession peerSession = sessions.get(peerId);
                            if (peerSession != null && peerSession.isOpen()) {
                                sendMessage(peerSession, message.getPayload());
                            } else {
                                handlePeerDisconnection(sessionId);
                            }
                        }
                    }
                    default -> logger.warn("Unknown message type '{}' from session {}", messageType, sessionId);
                }
            } catch (IOException e) {
                logger.error("Error parsing message from session {}", session.getId(), e);
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (isShuttingDown) return;

        String sessionId = session.getId();
        sessions.remove(sessionId);
        waitingUsers.remove(sessionId);
        handlePeerDisconnection(sessionId);
    }

    private void tryToPairUser(String newUserId) {
        if (peerMap.containsKey(newUserId)) {
            return;
        }

        while (true) {
            String waitingUserId = waitingUsers.poll();

            if (waitingUserId == null) {
                if (!waitingUsers.contains(newUserId)) {
                    waitingUsers.add(newUserId);
                }
                break;
            }

            WebSocketSession waitingUserSession = sessions.get(waitingUserId);
            if (waitingUserSession == null || !waitingUserSession.isOpen()) {
                continue;
            }
            
            if (waitingUserId.equals(newUserId)) {
                if (!waitingUsers.contains(newUserId)) {
                   waitingUsers.add(newUserId);
                }
                break;
            }

            WebSocketSession newUserSession = sessions.get(newUserId);
            if (newUserSession == null || !newUserSession.isOpen()) {
                logger.warn("New user {} disconnected before pairing.", newUserId);
                waitingUsers.add(waitingUserId);
                break;
            }

            peerMap.put(waitingUserId, newUserId);
            peerMap.put(newUserId, waitingUserId);

            sendMessage(waitingUserSession, "{\"type\": \"initiateOffer\"}");
            sendMessage(newUserSession, "{\"type\": \"waitForOffer\"}");
            break;
        }
    }

    private void handlePeerDisconnection(String disconnectedSessionId) {
        String peerId = peerMap.remove(disconnectedSessionId);
        if (peerId != null) {
            peerMap.remove(peerId);

            WebSocketSession remainingPeerSession = sessions.get(peerId);
            if (remainingPeerSession != null && remainingPeerSession.isOpen()) {
                sendMessage(remainingPeerSession, "{\"type\": \"leave\", \"reason\": \"Your partner disconnected\"}");
            }
        }
    }

    private void sendMessage(WebSocketSession session, String messagePayload) {
        if (session == null || !session.isOpen()) {
            logger.warn("Attempted to send message to closed or null session.");
            return;
        }
        executeIfNotShutdown(() -> {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(messagePayload));
                }
            } catch (IOException e) {
                logger.error("IOException sending message to session {}: {}", session.getId(), e.getMessage());
            } catch (IllegalStateException e) {
                logger.warn("IllegalStateException sending to session {}. It might be closing.", session.getId());
            }
        });
    }

    private void executeIfNotShutdown(Runnable task) {
        if (!isShuttingDown) {
            try {
                virtualThreadExecutor.execute(task);
            } catch (RejectedExecutionException e) {
                logger.warn("Task rejected, executor is shutting down.", e);
            }
        }
    }

    public int getActiveConnections() {
        return sessions.size();
    }
}