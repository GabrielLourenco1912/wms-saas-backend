package dev.lourencogabriel.wmssaas.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {
    private final JdbcTemplate jdbc;
    @GetMapping("/health")
    public Map<String,String> live(){return Map.of("status","UP");}
    @GetMapping("/health/ready")
    public ResponseEntity<Map<String,String>> ready(){
        try {jdbc.queryForObject("SELECT 1",Integer.class);return ResponseEntity.ok(Map.of("status","UP"));}
        catch(org.springframework.dao.DataAccessException exception){return ResponseEntity.status(503).body(Map.of("status","DOWN"));}
    }
}
