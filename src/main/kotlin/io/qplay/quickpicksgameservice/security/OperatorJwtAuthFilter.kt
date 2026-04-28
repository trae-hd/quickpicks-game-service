package io.qplay.quickpicksgameservice.security

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.qplay.quickpicksgameservice.tenant.TenantContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class OperatorJwtAuthFilter(
    @Value("\${app.security.operator-jwt-secret}") private val operatorJwtSecret: String,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val jwt = authHeader.substring(7)
        try {
            val signingKey = Keys.hmacShaKeyFor(operatorJwtSecret.toByteArray())
            val claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(jwt)
                .payload

            val operatorClaims = objectMapper.convertValue(claims, OperatorJwtClaims::class.java)

            // Set TenantContext for RLS
            TenantContext.setTenantId(operatorClaims.tenantId)

            val authentication = UsernamePasswordAuthenticationToken(
                operatorClaims,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${operatorClaims.role}"))
            )
            authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
            SecurityContextHolder.getContext().authentication = authentication

        } catch (e: Exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Operator JWT: ${e.message}")
            return
        }

        filterChain.doFilter(request, response)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return !request.servletPath.startsWith("/api/v1/admin")
    }
}
