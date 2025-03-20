package com.ddk.example.langchain4j.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Elijah Du
 * @date 2025/3/17
 */
@Slf4j
public class Calculator{

    @Tool
    private double add(long a, long b) {
        log.warn("add - Function call ....");
        return a + b;
    }

    @Tool
    private  double squareRoot(double x) {
        log.warn("squareRoot - Function call ....");
        return Math.sqrt(x);
    }
}
