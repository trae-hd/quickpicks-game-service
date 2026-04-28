package io.qplay.quickpicksgameservice.security

import io.qplay.quickpicksgameservice.tenant.TenantContext
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.HexFormat

@Component
class HostFeedHmacFilter(
    private val tenantRepository: TenantRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val tenantId = request.getHeader("X-QPlay-Tenant")
        if (tenantId.isNullOrBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-QPlay-Tenant header (Unauthorised)")
            return
        }

        val signature = request.getHeader("X-QPlay-Signature")
        if (signature.isNullOrBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-QPlay-Signature header (Unauthorised)")
            return
        }

        val tenant = tenantRepository.findById(tenantId).orElse(null)
        if (tenant == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unknown tenant (Unauthorised)")
            return
        }

        val isPlayerFeedPath = request.servletPath.startsWith("/api/v1/host/players")
        val hmacSecret = if (isPlayerFeedPath) {
            tenant.playerFeedHmacSecret?.takeIf { it.isNotBlank() } ?: tenant.walletHmacSecret
        } else {
            tenant.walletHmacSecret
        }

        val cachedRequest = CachedBodyHttpServletRequest(request)
        val bodyBytes = cachedRequest.getBodyBytes()

        val expectedSignature = computeHmac(hmacSecret, bodyBytes)
        if (!signature.equals(expectedSignature, ignoreCase = true)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid HMAC signature (Unauthorised)")
            return
        }

        if (isPlayerFeedPath && !tenant.playerFeedEnabled) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Player feed is not enabled for this tenant")
            return
        }

        TenantContext.setTenantId(tenantId)

        val authentication = UsernamePasswordAuthenticationToken(
            HostFeedPrincipal(tenantId),
            null,
            listOf(SimpleGrantedAuthority("ROLE_HOST_FEED"))
        )
        SecurityContextHolder.getContext().authentication = authentication

        filterChain.doFilter(cachedRequest, response)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.servletPath.startsWith("/api/v1/host")

    private fun computeHmac(secret: String, body: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return HexFormat.of().formatHex(mac.doFinal(body))
    }
}

data class HostFeedPrincipal(val tenantId: String)

class CachedBodyHttpServletRequest(request: HttpServletRequest) : HttpServletRequestWrapper(request) {
    private val cachedBody: ByteArray = request.inputStream.readBytes()

    fun getBodyBytes(): ByteArray = cachedBody

    override fun getInputStream(): ServletInputStream = object : ServletInputStream() {
        private val stream = ByteArrayInputStream(cachedBody)
        override fun read(): Int = stream.read()
        override fun isFinished(): Boolean = stream.available() == 0
        override fun isReady(): Boolean = true
        override fun setReadListener(listener: ReadListener) {}
    }

    override fun getReader(): BufferedReader = BufferedReader(InputStreamReader(getInputStream()))
}
