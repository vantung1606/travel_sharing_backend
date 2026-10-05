package com.wayfare.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Realtime community event hub based on Server-Sent Events (SSE).
 * <p>
 * Clients subscribe once via {@code GET /api/realtime/community/stream} and receive
 * lightweight events (comment added/deleted, like changed). Event payloads only contain
 * IDs & counters - clients re-fetch comment content through the normal REST API, so no
 * private data is leaked through the broadcast channel.
 */
@Service
@Slf4j
public class CommunityRealtimeService {

    /** 0 = no server-side timeout; dead connections are cleaned on send failure / heartbeat. */
    private static final long EMITTER_TIMEOUT_MS = 0L;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(emitter, "completed"));
        emitter.onTimeout(() -> removeEmitter(emitter, "timeout"));
        emitter.onError(ex -> removeEmitter(emitter, "error: " + ex.getMessage()));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("subscribers", emitters.size()), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("[Realtime] Failed to send handshake event: {}", e.getMessage());
            removeEmitter(emitter, "handshake failed");
        }

        log.info("[Realtime] New community subscriber. Active connections: {}", emitters.size());
        return emitter;
    }

    public void publishCommentAdded(Long postId, Long commentId, Long parentId, String actorEmail, long commentCount) {
        Map<String, Object> payload = basePayload("COMMENT_ADDED", postId, actorEmail);
        payload.put("commentId", commentId);
        payload.put("parentId", parentId);
        payload.put("commentCount", commentCount);
        broadcast("comment", payload);
    }

    public void publishCommentDeleted(Long postId, Long commentId, String actorEmail, long commentCount) {
        Map<String, Object> payload = basePayload("COMMENT_DELETED", postId, actorEmail);
        payload.put("commentId", commentId);
        payload.put("commentCount", commentCount);
        broadcast("comment", payload);
    }

    public void publishLikeChanged(Long postId, String actorEmail, Object likeCount) {
        Map<String, Object> payload = basePayload("LIKE_CHANGED", postId, actorEmail);
        payload.put("likeCount", likeCount);
        broadcast("like", payload);
    }

    /** Heartbeat keeps proxies (Vite dev proxy, nginx) from closing idle connections. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        if (emitters.isEmpty()) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                removeEmitter(emitter, "heartbeat failed");
            }
        }
    }

    private Map<String, Object> basePayload(String type, Long postId, String actorEmail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("postId", postId);
        payload.put("actorEmail", actorEmail);
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }

    private void broadcast(String eventName, Map<String, Object> payload) {
        log.info("[Realtime] Broadcasting '{}' {} to {} subscriber(s)", eventName, payload.get("type"), emitters.size());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                removeEmitter(emitter, "send failed: " + e.getMessage());
            }
        }
    }

    private void removeEmitter(SseEmitter emitter, String reason) {
        if (emitters.remove(emitter)) {
            log.debug("[Realtime] Subscriber removed ({}). Active connections: {}", reason, emitters.size());
        }
    }
}
