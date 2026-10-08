package com.ohgiraffers.springdatajpa.controller;

import com.ohgiraffers.springdatajpa.service.MenuImageStorage;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Hidden
@RestController
@RequestMapping("/api/menu-images")
public class MenuImageController {
    private final MenuImageStorage storage;

    public MenuImageController(MenuImageStorage storage) { this.storage = storage; }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> image(@PathVariable String filename) {
        Resource resource = storage.load(filename);
        if (resource == null) return ResponseEntity.notFound().build();
        MediaType type = filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(type)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
                .body(resource);
    }
}
