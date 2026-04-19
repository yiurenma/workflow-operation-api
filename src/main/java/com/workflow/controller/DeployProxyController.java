package com.workflow.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/workflow/deploy")
public class DeployProxyController {

    @PostMapping("/proxy")
    public ResponseEntity<String> proxyPost(
        @RequestParam String targetUrl,
        @RequestHeader("Authorization") String auth,
        @RequestBody String body
    ) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", auth);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        try {
            return restTemplate.postForEntity(targetUrl, entity, String.class);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("Proxy error: " + e.getMessage());
        }
    }

    @PatchMapping("/proxy")
    public ResponseEntity<String> proxyPatch(
        @RequestParam String targetUrl,
        @RequestHeader("Authorization") String auth,
        @RequestBody String body
    ) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", auth);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        try {
            return restTemplate.exchange(targetUrl, HttpMethod.PATCH, entity, String.class);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("Proxy error: " + e.getMessage());
        }
    }
}
