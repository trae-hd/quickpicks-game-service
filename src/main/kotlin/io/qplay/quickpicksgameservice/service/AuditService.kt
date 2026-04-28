package io.qplay.quickpicksgameservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class AuditService(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun log(tenantId: String, actorId: String, action: String, targetId: String? = null, payload: Map<String, Any?>? = null) {
        val payloadJson = if (payload != null) {
            objectMapper.writeValueAsString(payload)
        } else null

        // Using native SQL as audit_log is globally defined but uses RLS. 
        // We assume the caller or some context has set the tenant GUC if needed, 
        // but for Audit we might want to bypass or explicitly set it.
        // Actually, JdbcTemplate will use the connection, and our TenantAwareDataSource will set it if TenantContext is set.
        jdbcTemplate.update(
            "INSERT INTO audit_log (tenant_id, actor_id, action, target_id, payload) VALUES (?, ?, ?, ?, ?::jsonb)",
            tenantId, actorId, action, targetId, payloadJson
        )
    }
}
