package com.ddk.event.starter.internal;

import com.ddk.event.starter.config.DdkEventProperties.OutsideTransaction;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;

import java.util.function.Supplier;

/**
 * 让事务外发布的检查发生在 Spring Modulith 登记事件之前。
 * <p>
 * Modulith 用自己的事件广播器在调用监听器之前把事件写进发布记录，而且不管当前有没有事务。只靠监听器来检查的话，
 * 抛出异常时那条记录已经写进去了：调用方以为这次操作失败了，记录却留在表里，日后重新投递未完成的事件时又被发了出去。
 * 所以这里给广播器包一层，在它登记之前先检查。用代理而不是包装类，是因为广播器同时还是 Modulith 对外提供的
 * {@code IncompleteEventPublications}，类型不能变。
 */
public final class GuardedMulticasterPostProcessor implements BeanPostProcessor {

    private final Supplier<OutsideTransaction> mode;

    public GuardedMulticasterPostProcessor(Supplier<OutsideTransaction> mode) {
        this.mode = mode;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME.equals(beanName)
                || !(bean instanceof ApplicationEventMulticaster)) {
            return bean;
        }
        ProxyFactory proxy = new ProxyFactory(bean);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice((MethodInterceptor) invocation -> {
            // 用 var：不同 JDK 对数组元素上的可空标注读法不一样，显式写出类型会在其中一些上通不过空值检查
            var arguments = invocation.getArguments();
            if ("multicastEvent".equals(invocation.getMethod().getName()) && arguments.length > 0
                    && arguments[0] instanceof PayloadApplicationEvent<?> event) {
                new TransactionalPublicationGuard(mode.get()).check(event.getPayload());
            }
            return invocation.proceed();
        });
        return proxy.getProxy(bean.getClass().getClassLoader());
    }
}
