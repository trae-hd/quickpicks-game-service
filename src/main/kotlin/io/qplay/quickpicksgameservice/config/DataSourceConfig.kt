package io.qplay.quickpicksgameservice.config

import io.qplay.quickpicksgameservice.tenant.TenantAwareDataSource
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

@Configuration
class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    fun dataSource(properties: DataSourceProperties): DataSource {
        val hikariDataSource = properties.initializeDataSourceBuilder().build()
        return TenantAwareDataSource(hikariDataSource)
    }
}
