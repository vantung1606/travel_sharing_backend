package com.wayfare.controller;

import com.wayfare.service.CommunityRealtimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/realtime")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class RealtimeController {

    private final CommunityRealtimeService communityRealtimeService;

    @GetMapping(value = "/community/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamCommunityEvents() {
        log.info("REST request to subscribe community realtime stream");
        return communityRealtimeService.subscribe();
    }
}
