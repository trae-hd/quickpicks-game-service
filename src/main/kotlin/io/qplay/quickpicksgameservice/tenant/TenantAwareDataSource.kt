package io.qplay.quickpicksgameservice.tenant

import org.springframework.jdbc.datasource.DelegatingDataSource
import java.sql.Connection
import javax.sql.DataSource

class TenantAwareDataSource(delegate: DataSource) : DelegatingDataSource(delegate) {

    override fun getConnection(): Connection {
        val connection = super.getConnection()
        applyTenantContext(connection)
        return connection
    }

    override fun getConnection(username: String, password: String): Connection {
        val connection = super.getConnection(username, password)
        applyTenantContext(connection)
        return connection
    }

    private fun applyTenantContext(connection: Connection) {
        // Always reset to the login user (postgres) so non-tenant operations run without RLS
        // bypass being inherited from a previous tenant-scoped checkout of the same physical connection.
        connection.createStatement().use { it.execute("RESET ROLE") }

        val tenantId = TenantContext.getOrNull() ?: return

        // SET LOCAL doesn't accept prepared-statement parameters in PostgreSQL.
        // set_config with is_local=false sets it at session level so it persists
        // into the transaction that Hibernate opens after getConnection() returns.
        connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)").use { stmt ->
            stmt.setString(1, tenantId)
            stmt.execute()
        }

        // Switch to the non-superuser app role so PostgreSQL RLS policies are enforced.
        // The qplay_app role is created by migration V036 with DML grants on all tables.
        connection.createStatement().use { it.execute("SET ROLE qplay_app") }
    }
}