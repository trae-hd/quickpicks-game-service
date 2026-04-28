package io.qplay.quickpicksgameservice.security

import io.qplay.quickpicksgameservice.tenant.TenantContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class TenantContextSetupFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            filterChain.doFilter(request, response)
        } finally {
            // Always clear TenantContext to prevent connection bleed in thread pool
            TenantContext.clear()
        }
    }
}
