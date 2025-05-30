package org.apereo.cas.config;

import org.apereo.cas.authentication.AuthenticationEventExecutionPlanConfigurer;
import org.apereo.cas.authentication.AuthenticationHandler;
import org.apereo.cas.authentication.CasSSLContext;
import org.apereo.cas.authentication.principal.PrincipalFactory;
import org.apereo.cas.authentication.principal.PrincipalFactoryUtils;
import org.apereo.cas.authentication.principal.PrincipalNameTransformerUtils;
import org.apereo.cas.authentication.principal.PrincipalResolver;
import org.apereo.cas.authentication.support.password.PasswordEncoderUtils;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.persondir.PersonDirectoryAttributeRepositoryPlanConfigurer;
import org.apereo.cas.redis.RedisAuthenticationHandler;
import org.apereo.cas.redis.RedisPersonAttributeDao;
import org.apereo.cas.redis.core.RedisObjectFactory;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.util.function.FunctionUtils;

import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apereo.services.persondir.IPersonAttributeDao;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This is {@link RedisAuthenticationConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 6.1.0
 */
@EnableConfigurationProperties(CasConfigurationProperties.class)
@ConditionalOnProperty(prefix = "cas.authn.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
@Configuration(value = "RedisAuthenticationConfiguration", proxyBeanMethods = false)
public class RedisAuthenticationConfiguration {

    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    @ConditionalOnMissingBean(name = "redisPrincipalFactory")
    public PrincipalFactory redisPrincipalFactory() {
        return PrincipalFactoryUtils.newPrincipalFactory();
    }

    @Bean
    @ConditionalOnMissingBean(name = "redisAuthenticationConnectionFactory")
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public RedisConnectionFactory redisAuthenticationConnectionFactory(
        @Qualifier(CasSSLContext.BEAN_NAME)
        final CasSSLContext casSslContext,
        final CasConfigurationProperties casProperties) {
        val redis = casProperties.getAuthn().getRedis();
        return RedisObjectFactory.newRedisConnectionFactory(redis, casSslContext);
    }

    @Bean(name = {"authenticationRedisTemplate", "redisTemplate"})
    @ConditionalOnMissingBean(name = "authenticationRedisTemplate")
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public RedisTemplate authenticationRedisTemplate(
        @Qualifier("redisAuthenticationConnectionFactory")
        final RedisConnectionFactory redisAuthenticationConnectionFactory) {
        return RedisObjectFactory.newRedisTemplate(redisAuthenticationConnectionFactory);
    }

    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    @ConditionalOnMissingBean(name = "redisAuthenticationHandler")
    public AuthenticationHandler redisAuthenticationHandler(final CasConfigurationProperties casProperties, final ConfigurableApplicationContext applicationContext,
                                                            @Qualifier("redisPrincipalFactory")
                                                            final PrincipalFactory redisPrincipalFactory,
                                                            @Qualifier("authenticationRedisTemplate")
                                                            final RedisTemplate authenticationRedisTemplate,
                                                            @Qualifier(ServicesManager.BEAN_NAME)
                                                            final ServicesManager servicesManager) {
        val redis = casProperties.getAuthn().getRedis();
        val handler = new RedisAuthenticationHandler(redis.getName(), servicesManager, redisPrincipalFactory, redis.getOrder(), authenticationRedisTemplate);
        handler.setPrincipalNameTransformer(PrincipalNameTransformerUtils.newPrincipalNameTransformer(redis.getPrincipalTransformation()));
        handler.setPasswordEncoder(PasswordEncoderUtils.newPasswordEncoder(redis.getPasswordEncoder(), applicationContext));
        return handler;
    }

    @ConditionalOnMissingBean(name = "redisAuthenticationEventExecutionPlanConfigurer")
    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public AuthenticationEventExecutionPlanConfigurer redisAuthenticationEventExecutionPlanConfigurer(
        @Qualifier("redisAuthenticationHandler")
        final AuthenticationHandler redisAuthenticationHandler,
        @Qualifier(PrincipalResolver.BEAN_NAME_PRINCIPAL_RESOLVER)
        final PrincipalResolver defaultPrincipalResolver) {
        return plan -> plan.registerAuthenticationHandlerWithPrincipalResolver(redisAuthenticationHandler, defaultPrincipalResolver);
    }

    @ConditionalOnMissingBean(name = "redisPersonAttributeDaos")
    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public List<IPersonAttributeDao> redisPersonAttributeDaos(
        @Qualifier(CasSSLContext.BEAN_NAME)
        final CasSSLContext casSslContext,
        final CasConfigurationProperties casProperties) {
        val redis = casProperties.getAuthn().getAttributeRepository().getRedis();
        return redis.stream().filter(r -> StringUtils.isNotBlank(r.getHost())).map(r -> {
            val conn = RedisObjectFactory.newRedisConnectionFactory(r, true, casSslContext);
            val template = RedisObjectFactory.newRedisTemplate(conn);
            template.afterPropertiesSet();
            val cb = new RedisPersonAttributeDao(template);
            cb.setOrder(r.getOrder());
            FunctionUtils.doIfNotNull(r.getId(), cb::setId);
            return cb;
        }).collect(Collectors.toList());
    }

    @ConditionalOnMissingBean(name = "redisAttributeRepositoryPlanConfigurer")
    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public PersonDirectoryAttributeRepositoryPlanConfigurer redisAttributeRepositoryPlanConfigurer(
        @Qualifier("redisPersonAttributeDaos")
        final List<IPersonAttributeDao> redisPersonAttributeDaos) {
        return plan -> redisPersonAttributeDaos.forEach(plan::registerAttributeRepository);
    }
}
