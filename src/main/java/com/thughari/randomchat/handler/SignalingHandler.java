package com.thughari.randomchat.handler;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.annotation.PreDestroy;

@Component
public class SignalingHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(SignalingHandler.class);
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Queue<String> waitingUsers = new LinkedList<>();
    private final Map<String, String> peerMap = new ConcurrentHashMap<>();
    
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
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        virtualThreadExecutor.execute(() -> {
            try {
                sessions.put(session.getId(), session);
                logger.info("Connection established: {}. Total sessions: {}", session.getId(), sessions.size());

                synchronized (waitingUsers) {
                    if (waitingUsers.isEmpty()) {
                        waitingUsers.add(session.getId());
                        logger.info("User {} added to waiting queue", session.getId());
                    } else {
                        handlePairing(session);
                    }
                }
            } catch (Exception e) {
                logger.error("Error in connection establishment", e);
            }
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        executeIfNotShutdown(() -> {
            try {
                String sessionId = session.getId();
                String peerId = peerMap.get(sessionId);

                if (peerId != null) {
                    handlePeerMessage(session, message, sessionId, peerId);
                } else {
                    logger.warn("No peer found for {}", sessionId);
                }
            } catch (Exception e) {
                logger.error("Error handling message", e);
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (!isShuttingDown) {
            try {
                String sessionId = session.getId();
                sessions.remove(sessionId);
                synchronized (waitingUsers) {
                    waitingUsers.remove(sessionId);
                }
                handlePeerDisconnection(sessionId);
                logger.info("Total active sessions after close: {}", sessions.size());
            } catch (Exception e) {
                logger.error("Error in connection closure", e);
            }
        }
    }
    
    private void executeIfNotShutdown(Runnable task) {
        if (!isShuttingDown) {
            try {
                virtualThreadExecutor.execute(task);
            } catch (RejectedExecutionException e) {
                logger.warn("Task rejected, executor might be shutting down", e);
            }
        }
    }

    private void handlePairing(WebSocketSession session) {
        String peer1Id = waitingUsers.poll();
        String peer2Id = session.getId();

        WebSocketSession peer1Session = sessions.get(peer1Id);
        if (peer1Session == null || !peer1Session.isOpen()) {
            waitingUsers.add(peer2Id);
            return;
        }

        peerMap.put(peer2Id, peer1Id);
        peerMap.put(peer1Id, peer2Id);

        virtualThreadExecutor.execute(() -> {
            sendMessage(peer1Session, "{\"type\": \"initiateOffer\", \"peerId\": \"" + peer2Id + "\"}");
            sendMessage(session, "{\"type\": \"waitForOffer\", \"peerId\": \"" + peer1Id + "\"}");
        });
    }

    private void handlePeerMessage(WebSocketSession session, TextMessage message, String sessionId, String peerId) {
        WebSocketSession peerSession = sessions.get(peerId);
        if (peerSession != null && peerSession.isOpen()) {
            virtualThreadExecutor.execute(() -> {
                sendMessage(peerSession, message.getPayload());
            });
        } else {
            handleDisconnectedPeer(session, sessionId);
        }
    }

    private void handlePeerDisconnection(String sessionId) {
        String peerId = peerMap.remove(sessionId);
        if (peerId != null) {
            peerMap.remove(peerId);
            WebSocketSession peerSession = sessions.get(peerId);
            if (peerSession != null && peerSession.isOpen()) {
                virtualThreadExecutor.execute(() -> {
                    sendMessage(peerSession, "{\"type\": \"leave\", \"reason\": \"Your partner disconnected\"}");
                    addSessionToWaitingQueueIfNotPresent(peerId, "partner disconnected");
                });
            }
        }
    }

    private void handleDisconnectedPeer(WebSocketSession session, String sessionId) {
        virtualThreadExecutor.execute(() -> {
            sendMessage(session, "{\"type\": \"peer_disconnected\"}");
            peerMap.remove(sessionId);
            addSessionToWaitingQueueIfNotPresent(sessionId, "peer was unavailable");
        });
    }

    private void sendMessage(WebSocketSession session, String messagePayload) {
        try {
            if (session != null && session.isOpen()) {
                synchronized (session) {
                	session.sendMessage(new TextMessage(messagePayload));
                }
            } else {
                logger.warn("Attempted to send message to closed or null session (ID: {})", session != null ? session.getId() : "unknown");
            }
        } catch (IOException e) {
            logger.error("Error sending message to session {}: {}", session != null ? session.getId() : "unknown", e.getMessage(), e);
            if (session != null) {
                try {
                    if(session.isOpen()){
                       session.close(CloseStatus.PROTOCOL_ERROR.withReason("Failed to send message"));
                    }
                } catch (IOException ex) {
                    logger.error("Error closing session {} after send failure: {}", session.getId(), ex.getMessage());
                }
            }
        } catch (IllegalStateException e) {
             logger.warn("IllegalStateException sending message to session {}: {}. Session might be closing.", session != null ? session.getId() : "unknown", e.getMessage());
        }
    }

    private void addSessionToWaitingQueueIfNotPresent(String sessionId, String reason) {
        synchronized (waitingUsers) {
            if (sessions.containsKey(sessionId) && !waitingUsers.contains(sessionId) && !peerMap.containsKey(sessionId)) {
                waitingUsers.add(sessionId);
                logger.info("User {} added back to waiting queue (reason: {}). Waiting users: {}. Queue: {}",
                        sessionId, reason, waitingUsers.size(), waitingUsers);
                sendMessage(sessions.get(sessionId), "{\"type\": \"status\", \"message\": \"You are back in the waiting queue. Reason: " + reason + "\"}");
            } else {
                 logger.info("User {} not added back to waiting queue. Session exists: {}, In waiting: {}, In peerMap: {}",
                        sessionId, sessions.containsKey(sessionId), waitingUsers.contains(sessionId), peerMap.containsKey(sessionId));
            }
        }
    }
}