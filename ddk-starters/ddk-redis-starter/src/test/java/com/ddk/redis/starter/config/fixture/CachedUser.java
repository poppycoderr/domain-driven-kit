package com.ddk.redis.starter.config.fixture;

import java.time.LocalDateTime;
import java.util.List;

public record CachedUser(Long id, String name, LocalDateTime createdAt, List<String> roles) {
}
