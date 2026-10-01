package com.ddk.concurrency.starter.internal;

import com.ddk.concurrency.starter.AggregateLock;
import com.ddk.concurrency.starter.Idempotent;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.autoproxy.AbstractBeanFactoryAwareAdvisingPostProcessor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.core.Ordered;

/**
 * 为声明了 {@link AggregateLock} 或 {@link Idempotent} 方法的 Bean 挂上拦截。
 * <p>
 * 排在其他后置处理器之后执行，并把拦截放在已有通知之前：Bean 已经被事务代理时，锁包在事务外面，事务提交之后才释放。
 * 顺序反过来的话，锁释放时事务还没提交，下一个操作会读到旧状态。
 */
public class ConcurrencyAdvisingPostProcessor extends AbstractBeanFactoryAwareAdvisingPostProcessor {

    public ConcurrencyAdvisingPostProcessor(MethodInterceptor interceptor) {
        ComposablePointcut pointcut = new ComposablePointcut(AnnotationMatchingPointcut.forMethodAnnotation(AggregateLock.class))
                .union(AnnotationMatchingPointcut.forMethodAnnotation(Idempotent.class));
        this.advisor = new DefaultPointcutAdvisor(pointcut, interceptor);
        setBeforeExistingAdvisors(true);
        setOrder(Ordered.LOWEST_PRECEDENCE);
    }
}
