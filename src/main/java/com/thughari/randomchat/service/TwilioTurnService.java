package com.thughari.randomchat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thughari.randomchat.component.TwilioHttpClient;
import com.thughari.randomchat.exceptions.TwilioClientException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections; // For Collections.singletonList
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TwilioTurnService {

    private static final Logger logger = LoggerFactory.getLogger(TwilioTurnService.class);

    private final TwilioHttpClient twilioHttpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicReference<List<Map<String, String>>> cachedIceServers = new AtomicReference<>(new ArrayList<>());
    private final AtomicLong cacheExpiryTime = new AtomicLong(0);
    private final long CACHE_TTL_SECONDS = 60 * 60; // Cache for 1 hour

    // Google STUN server
    private static final Map<String, String> GOOGLE_STUN_SERVER = Map.of("urls", "stun:stun.l.google.com:19302");

    // Constructor injection
    public TwilioTurnService(TwilioHttpClient twilioHttpClient) {
        this.twilioHttpClient = twilioHttpClient;
    }

    public List<Map<String, String>> getTwilioIceServers() {
        // Check cache first
        if (!cachedIceServers.get().isEmpty() && System.currentTimeMillis() < cacheExpiryTime.get()) {
            logger.info("Returning Twilio ICE servers from cache.");
            return cachedIceServers.get();
        }

        logger.info("Fetching new Twilio ICE servers from API.");
        return fetchNewTwilioIceServers();
    }

    private synchronized List<Map<String, String>> fetchNewTwilioIceServers() {
        // Double-check locking for cache refresh
        if (!cachedIceServers.get().isEmpty() && System.currentTimeMillis() < cacheExpiryTime.get()) {
            logger.info("Another thread already refreshed cache, returning from cache.");
            return cachedIceServers.get();
        }

        List<Map<String, String>> iceServersToReturn = new ArrayList<>();
        iceServersToReturn.add(GOOGLE_STUN_SERVER);

        try {
            String responseBody = twilioHttpClient.fetchTwilioTokensApiResponse();

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode twilioIceServersNode = root.path("ice_servers");

            if (twilioIceServersNode.isArray()) {
                for (JsonNode serverNode : twilioIceServersNode) {
                    Map<String, String> serverConfig = new HashMap<>();
                    // Parse URLs
                    if (serverNode.has("urls")) {
                        if (serverNode.get("urls").isArray()) {
                            List<String> urlsList = new ArrayList<>();
                            for (JsonNode urlItem : serverNode.get("urls")) {
                                urlsList.add(urlItem.asText());
                            }
                            serverConfig.put("urls", String.join(",", urlsList));
                        } else {
                            serverConfig.put("urls", serverNode.get("urls").asText());
                        }
                    } else if (serverNode.has("url")) {
                        serverConfig.put("urls", serverNode.get("url").asText());
                    }

                    if (serverConfig.containsKey("urls") && !serverConfig.get("urls").equals(GOOGLE_STUN_SERVER.get("urls"))) {
                        if (serverNode.has("username")) {
                            serverConfig.put("username", serverNode.get("username").asText());
                        }
                        if (serverNode.has("credential")) {
                            serverConfig.put("credential", serverNode.get("credential").asText());
                        }
                        iceServersToReturn.add(serverConfig);
                    }
                }
            }

            // Update cache
            cachedIceServers.set(iceServersToReturn);
            cacheExpiryTime.set(System.currentTimeMillis() + (CACHE_TTL_SECONDS * 1000));
            logger.info("Successfully fetched Twilio ICE servers and combined with Google STUN. Total: {} servers.", iceServersToReturn.size());
            return iceServersToReturn;

        } catch (TwilioClientException e) {
            logger.error("Error communicating with Twilio API: {}", e.getMessage());
            // Fallback to Google STUN server
            logger.warn("Twilio API error, returning only Google STUN server.");
            updateCacheWithGoogleStunOnly();
            return Collections.singletonList(GOOGLE_STUN_SERVER);
        } catch (Exception e) {
            logger.error("Error processing Twilio API response or unexpected issue: {}", e.getMessage(), e);
            logger.warn("Unexpected error, returning only Google STUN server.");
            updateCacheWithGoogleStunOnly();
            return Collections.singletonList(GOOGLE_STUN_SERVER);
        }
    }

    private void updateCacheWithGoogleStunOnly() {
        cachedIceServers.set(Collections.singletonList(GOOGLE_STUN_SERVER));
        cacheExpiryTime.set(System.currentTimeMillis() + (CACHE_TTL_SECONDS * 1000));
    }
}