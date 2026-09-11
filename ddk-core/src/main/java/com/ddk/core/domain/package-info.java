/**
 * DDD 领域模型基类。
 * <p>
 * 核心只有三件事：给身份一个类型（{@link com.ddk.core.domain.Identifier}）、
 * 区分按值相等与按身份相等（{@link com.ddk.core.domain.ValueObject} /
 * {@link com.ddk.core.domain.Entity}）、给领域事件一个收集与发布的位置
 * （{@link com.ddk.core.domain.AggregateRoot} / {@link com.ddk.core.domain.DomainEvent}）。
 * <p>
 * <b>硬约束：本包不得依赖 Spring、MyBatis、Jackson 或任何框架。</b>
 * 领域模型必须能在没有容器的情况下被单元测试，这条由
 * {@code CommonArchRules.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS} 强制执行。
 * <p>
 * 基类只提供机制、不强制流程，也不要求整套接受——只想用
 * {@code ValueObject} 就只用它。
 *
 * @author Elijah Du
 */
package com.ddk.core.domain;
