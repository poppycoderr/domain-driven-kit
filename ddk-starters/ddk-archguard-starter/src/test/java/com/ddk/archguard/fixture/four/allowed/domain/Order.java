package com.ddk.archguard.fixture.four.allowed.domain;

import com.ddk.core.domain.Identifier;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

public class Order {

    @Nullable
    Identifier<Long> id;

    List<Instant> history;
}
