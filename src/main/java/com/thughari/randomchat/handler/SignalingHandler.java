package com.thughari.randomchat.handler;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class SignalingHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(SignalingHandler.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Queue<String> waitingUsers = new LinkedList<>(); // Access to this needs to be synchronized
    private final Map<String, String> peerMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        logger.info("Connection established: {}. Total sessions: {}", session.getId(), sessions.size());

        synchronized (waitingUsers) {
            if (waitingUsers.isEmpty()) {
                waitingUsers.add(session.getId());
                logger.info("User {} added to waiting queue. Waiting users: {}", session.getId(), waitingUsers.size());
            } else {
                String peer1Id = waitingUsers.poll();
                String peer2Id = session.getId(); // This is the current session

                WebSocketSession peer1Session = sessions.get(peer1Id);
                if (peer1Session == null || !peer1Session.isOpen()) {
                    logger.warn("Peer {} (peer1) disconnected before pairing. Adding {} (peer2) back to queue.", peer1Id, peer2Id);
                    waitingUsers.add(peer2Id); // Add current user (peer2) to wait again
                    // No need to remove from peerMap as pairing didn't complete for peer1
                    return;
                }

                peerMap.put(peer2Id, peer1Id);
                peerMap.put(peer1Id, peer2Id);
                logger.info("Paired: {} (peer1) with {} (peer2). PeerMap size: {}", peer1Id, peer2Id, peerMap.size());

                // Designate one to initiate offer to avoid glare
                // peer1 (already waiting) will initiate
                // peer2 (newly connected, current 'session') will wait for offer
                sendMessage(peer1Session, "{\"type\": \"initiateOffer\", \"peerId\": \"" + peer2Id + "\"}");
                sendMessage(session, "{\"type\": \"waitForOffer\", \"peerId\": \"" + peer1Id + "\"}"); // Corrected: use 'session' for peer2
                logger.info("Sent initiateOffer to {} (peer1) and waitForOffer to {} (peer2)", peer1Id, peer2Id);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String peerId = peerMap.get(session.getId());
        logger.info("Message from {}: {}. Relaying to peer: {}", session.getId(), message.getPayload(), peerId);

        if (peerId != null) {
            WebSocketSession peerSession = sessions.get(peerId);
            if (peerSession != null && peerSession.isOpen()) {
                sendMessage(peerSession, message.getPayload()); // Send the raw payload
            } else {
                logger.warn("Peer {} for {} not found or session closed. Cannot relay message.", peerId, session.getId());
                // Optionally, notify the sender that their peer is gone
                // sendMessage(session, "{\"type\": \"peer_unavailable\"}");
            }
        } else {
            logger.warn("No peer found for {}. Message: {}. This might happen if user is waiting or recently disconnected.", session.getId(), message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        logger.info("Connection closed: {} with status {}. sessions before removal: {}", sessionId, status, sessions.size());
        sessions.remove(sessionId);

        boolean removedFromWaiting;
        synchronized (waitingUsers) {
            removedFromWaiting = waitingUsers.remove(sessionId);
        }
        if (removedFromWaiting) {
            logger.info("User {} removed from waiting queue.", sessionId);
        }

        String peerId = peerMap.remove(sessionId);
        if (peerId != null) {
            peerMap.remove(peerId); // Remove the reverse mapping as well
            logger.info("Removed pair: {} and {}. PeerMap size: {}", sessionId, peerId, peerMap.size());
            WebSocketSession peerSession = sessions.get(peerId);
            if (peerSession != null && peerSession.isOpen()) {
                logger.info("Notifying peer {} about disconnection of {}", peerId, sessionId);
                sendMessage(peerSession, "{\"type\": \"leave\"}");
                // Decide if the remaining peer should go back to waiting queue
                // synchronized (waitingUsers) {
                //    if (!waitingUsers.contains(peerId) && sessions.containsKey(peerId)) { // Check if not already waiting
                //        waitingUsers.add(peerId);
                //        logger.info("Peer {} added back to waiting queue after their partner left.", peerId);
                //    }
                // }
            } else {
                logger.info("Peer {} for {} (disconnected session) not found or session already closed. Cannot send 'leave' message.", peerId, sessionId);
            }
        } else if (!removedFromWaiting) { // Only log if not removed from waiting and no peer found
            logger.info("No peer found for {} upon closing, and user was not in waiting queue.", sessionId);
        }
        logger.info("Total sessions after removal: {}", sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("Transport error for session {}: {}", session != null ? session.getId() : "UNKNOWN", exception.getMessage(), exception);
        if (session != null) {
            // Consider this like a connection closed event
            afterConnectionClosed(session, CloseStatus.PROTOCOL_ERROR);
        }
    }

    private void sendMessage(WebSocketSession session, String messagePayload) {
        try {
            if (session != null && session.isOpen()) {
                session.sendMessage(new TextMessage(messagePayload));
            } else {
                logger.warn("Attempted to send message to closed or null session (ID: {})", session != null ? session.getId() : "unknown");
            }
        } catch (IOException e) {
            logger.error("Error sending message to session {}: {}", session != null ? session.getId() : "unknown", e.getMessage(), e);
        }
    }
}