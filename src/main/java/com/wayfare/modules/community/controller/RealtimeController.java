package com.wayfare.modules.community.controller;

import com.wayfare.modules.community.service.CommunityRealtimeService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/realtime")
@CrossOrigin(origins = "*")
@Slf4j
public class RealtimeController {

    private final CommunityRealtimeService communityRealtimeService;

    public RealtimeController(CommunityRealtimeService communityRealtimeService) {
        this.communityRealtimeService = communityRealtimeService;
    }

    @GetMapping(value = "/community/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamCommunityEvents(HttpServletResponse response) {
        // Essential headers to avoid buffering through reverse proxies (Railway, Cloudflare, Nginx)
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        log.info("REST request to subscribe community realtime stream");
        return communityRealtimeService.subscribe();
    }
}


