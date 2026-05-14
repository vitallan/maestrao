package com.allanvital.maestrao.artifactproxy.web;

import com.allanvital.maestrao.artifactproxy.service.ArtifactCacheService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/maven")
@ConditionalOnProperty(prefix = "maestrao.artifact-proxy", name = "enabled", havingValue = "true")
public class MavenProxyController {

    private final ArtifactCacheService cacheService;

    public MavenProxyController(ArtifactCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @GetMapping("/**")
    public ResponseEntity<Resource> fetch(HttpServletRequest request) {
        String path = extractPath(request);
        ArtifactCacheService.ResolutionResult result = cacheService.resolve(path);
        if (result.status() != 200 || result.path() == null) {
            return ResponseEntity.status(result.status()).body(new ByteArrayResource(result.message().getBytes()));
        }
        try {
            long size = result.size();
            HttpHeaders headers = new HttpHeaders();
            if (size >= 0) {
                headers.setContentLength(size);
            }
            headers.setContentType(mediaType(path));
            return new ResponseEntity<>(new InputStreamResource(result.stream()), headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ByteArrayResource(e.getMessage().getBytes()));
        }
    }

    private String extractPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String base = request.getContextPath() + "/maven/";
        if (uri.startsWith(base)) {
            return uri.substring(base.length());
        }
        if (uri.startsWith("/maven/")) {
            return uri.substring("/maven/".length());
        }
        return "";
    }

    private MediaType mediaType(String path) {
        if (path.endsWith(".pom") || path.endsWith(".xml")) {
            return MediaType.APPLICATION_XML;
        }
        if (path.endsWith(".sha1") || path.endsWith(".md5") || path.endsWith(".sha256")) {
            return MediaType.TEXT_PLAIN;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
