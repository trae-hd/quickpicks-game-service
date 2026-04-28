package io.qplay.quickpicksgameservice.tenant

object TenantContext {
    private val currentTenant = ThreadLocal<String?>()
    private val currentPlayer = ThreadLocal<String?>()

    fun setTenantId(tenantId: String?) {
        currentTenant.set(tenantId)
    }

    fun getTenantId(): String? = getOrNull()

    fun getOrNull(): String? = currentTenant.get()

    fun getRequiredTenantId(): String = getOrNull() ?: throw IllegalStateException("Tenant ID not set")

    fun setPlayerId(playerId: String?) {
        currentPlayer.set(playerId)
    }

    fun getPlayerId(): String? = currentPlayer.get()

    fun getRequiredPlayerId(): String = getPlayerId() ?: throw IllegalStateException("Player ID not set")

    fun clear() {
        currentTenant.remove()
        currentPlayer.remove()
    }
}
