package com.example.mall.platform;

import com.ddk.core.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 认证与授权。
 * <p>
 * 默认 profile 是开发模式：放行全部请求，身份来自请求头，见 {@link CustomerIdentity}。{@value #PROFILE} profile 要求登录（HTTP Basic），按角色授权：
 * <ul>
 *     <li>顾客（{@code CUSTOMER}）：自己的订单和支付，查库存；</li>
 *     <li>客服（{@code SUPPORT}）：读模型重建、库存变更与预占查询、actuator，以及在 MCP 工具里代任何顾客操作；</li>
 *     <li>{@code /mcp} 需要登录，工具里再按 {@link CustomerAccess} 检查能不能代参数里的顾客操作；</li>
 *     <li>健康检查和接口文档公开。</li>
 * </ul>
 * 用户目录只是演示：用户名是顾客 ID 的都是顾客，{@code support} 是客服，口令都是 {@code mall.security.demo-password}。
 * 真实项目里换成 OAuth2 资源服务器或企业的身份提供方，只要认证结果的用户名是顾客 ID、角色对得上，其余代码不用改。
 */
@Configuration(proxyBeanMethods = false)
public class MallSecurity {

    public static final String PROFILE = "secure";

    static final String CUSTOMER = "CUSTOMER";

    static final String SUPPORT = "SUPPORT";

    @Bean
    @Profile("!" + PROFILE)
    SecurityFilterChain developmentFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    @Profile(PROFILE)
    SecurityFilterChain secureFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/orders/search/rebuild", "/orders/search/status").hasRole(SUPPORT)
                        .requestMatchers(HttpMethod.GET, "/inventory/{skuId}").authenticated()
                        .requestMatchers("/inventory/**").hasRole(SUPPORT)
                        .requestMatchers("/orders/**", "/payments/**").hasRole(CUSTOMER)
                        .requestMatchers("/mcp", "/mcp/**").authenticated()
                        .anyRequest().hasRole(SUPPORT))
                .httpBasic(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    @Profile(PROFILE)
    UserDetailsService demoUsers(@Value("${mall.security.demo-password}") String demoPassword) {
        String password = "{noop}" + demoPassword;
        return username -> {
            if ("support".equals(username)) {
                return User.withUsername(username).password(password).roles(SUPPORT).build();
            }
            if (username.matches("[1-9]\\d{0,17}")) {
                return User.withUsername(username).password(password).roles(CUSTOMER).build();
            }
            throw new UsernameNotFoundException(username);
        };
    }

    @Bean
    @Profile("!" + PROFILE)
    CustomerAccess openCustomerAccess() {
        return new CustomerAccess() {

            @Override
            public void actFor(long customerId) {
            }

            @Override
            public void requireSupport() {
            }
        };
    }

    @Bean
    @Profile(PROFILE)
    CustomerAccess securedCustomerAccess() {
        return new CustomerAccess() {

            @Override
            public void actFor(long customerId) {
                if (!isSupport() && !String.valueOf(customerId).equals(currentName())) {
                    throw new BusinessException(PlatformError.ACTING_FOR_OTHER_CUSTOMER, customerId);
                }
            }

            @Override
            public void requireSupport() {
                if (!isSupport()) {
                    throw new BusinessException(PlatformError.SUPPORT_REQUIRED);
                }
            }
        };
    }

    private static boolean isSupport() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream().anyMatch(a -> ("ROLE_" + SUPPORT).equals(a.getAuthority()));
    }

    private static String currentName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }
}
