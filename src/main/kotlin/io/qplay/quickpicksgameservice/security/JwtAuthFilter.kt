package io.qplay.quickpicksgameservice.security

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.qplay.quickpicksgameservice.tenant.TenantContext
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Base64

@Component
class JwtAuthFilter(
    private val tenantRepository: TenantRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${app.security.operator-jwt-secret}") private val operatorJwtSecret: String
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
            // Step 1: Base64-decode the JWT payload (no signature verification) to extract tenant ID.
            // parseUnsecuredClaims() throws on signed tokens — base64 decoding is the correct approach.
            val parts = jwt.split(".")
            if (parts.size != 3) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT format (Unauthorised)")
                return
            }
            val payloadJson = Base64.getUrlDecoder().decode(parts[1])
            val tenantId = objectMapper.readTree(payloadJson).get("tenant")?.asText()

            if (tenantId.isNullOrBlank()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing tenant in JWT (Unauthorised)")
                return
            }

            val tenant = tenantRepository.findById(tenantId).orElse(null)
            if (tenant == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unknown tenant (Unauthorised)")
                return
            }

            // Step 2: Full signature verification.
            // Preview JWTs are minted by the frontend using the operator secret — use that for
            // verification. All other player JWTs use the tenant-specific secret.
            val isPreview = objectMapper.readTree(payloadJson).get("preview")?.asBoolean() == true
            val secretBytes = if (isPreview) operatorJwtSecret.toByteArray() else tenant.jwtSecret.toByteArray()
            val signingKey = Keys.hmacShaKeyFor(secretBytes)
            val claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(jwt)
                .payload

            val playerClaims = objectMapper.convertValue(claims, PlayerJwtClaims::class.java)

            // Set TenantContext for RLS
            TenantContext.setTenantId(playerClaims.tenantId)
            TenantContext.setPlayerId(playerClaims.playerId)

            val authentication = UsernamePasswordAuthenticationToken(
                playerClaims,
                null,
                listOf(SimpleGrantedAuthority("ROLE_PLAYER"))
            )
            authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
            SecurityContextHolder.getContext().authentication = authentication

        } catch (e: Exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT: ${e.message}")
            return
        }

        filterChain.doFilter(request, response)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return request.servletPath.startsWith("/api/v1/admin") || 
               request.servletPath.startsWith("/actuator") ||
               request.servletPath.startsWith("/v3/api-docs") ||
               request.servletPath.startsWith("/swagger-ui")
    }
}
