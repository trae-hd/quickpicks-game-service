package io.qplay.quickpicksgameservice.config

import io.qplay.quickpicksgameservice.security.HostFeedHmacFilter
import io.qplay.quickpicksgameservice.security.JwtAuthFilter
import io.qplay.quickpicksgameservice.security.OperatorJwtAuthFilter
import io.qplay.quickpicksgameservice.security.TenantContextSetupFilter
import io.qplay.quickpicksgameservice.tenant.TenantContext
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val operatorJwtAuthFilter: OperatorJwtAuthFilter,
    private val tenantContextSetupFilter: TenantContextSetupFilter,
    private val hostFeedHmacFilter: HostFeedHmacFilter,
    private val tenantRepository: TenantRepository
) {
    private val objectMapper = jacksonObjectMapper()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/api/v1/admin/**").hasAnyRole("TENANT_ADMIN", "PLATFORM_ADMIN", "REVIEWER")
                    .requestMatchers("/api/v1/host/**").hasRole("HOST_FEED")
                    .anyRequest().authenticated()
            }
            .addFilterBefore(tenantContextSetupFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(hostFeedHmacFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(operatorJwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        val config = object : CorsConfiguration() {
            override fun checkOrigin(origin: String?): String? {
                if (origin == null) return null

                val tenantId = TenantContext.getTenantId()
                if (tenantId == null) {
                    // No tenant context — this is a CORS preflight (OPTIONS) which carries no JWT,
                    // so the JWT filters haven't run yet. Check the origin against all tenants so
                    // that the browser's preflight passes and the real request is allowed through.
                    val allowed = tenantRepository.findAll().any { tenant ->
                        val origins = runCatching { objectMapper.readValue<List<String>>(tenant.allowedHostOrigins) }
                            .getOrDefault(emptyList())
                        origins.contains("*") || origins.contains(origin)
                    }
                    return if (allowed) origin else null
                }

                val tenant = tenantRepository.findById(tenantId).orElse(null) ?: return null
                val allowedOrigins: List<String> = objectMapper.readValue(tenant.allowedHostOrigins)

                return if (allowedOrigins.contains("*") || allowedOrigins.contains(origin)) {
                    origin
                } else {
                    null
                }
            }
        }.apply {
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "Idempotency-Key")
            allowCredentials = true
            maxAge = 3600L
        }
        
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
