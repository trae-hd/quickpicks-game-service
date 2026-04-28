package io.qplay.quickpicksgameservice.controller

import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.service.trending.TrendingApplicationService
import io.qplay.quickpicksgameservice.tenant.TenantContext
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Profile("api")
@RequestMapping("/api/v1/rounds/{roundId}/trending")
class TrendingController(
    private val trendingService: TrendingApplicationService
) {
    @GetMapping
    fun getTrending(@PathVariable roundId: UUID): ApiResponse<TrendingResponse> {
        val tenantId = TenantContext.getTenantId() ?: throw IllegalStateException("No tenant context")
        val data = trendingService.getTrending(roundId, tenantId)
        return ApiResponse(TrendingResponse(roundId, data))
    }
}

data class TrendingResponse(
    val roundId: UUID,
    val picks: Map<String, Map<String, Int>>
)
