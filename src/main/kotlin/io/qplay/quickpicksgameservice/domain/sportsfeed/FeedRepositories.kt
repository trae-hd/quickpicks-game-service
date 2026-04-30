package io.qplay.quickpicksgameservice.domain.sportsfeed

import org.springframework.data.jpa.repository.JpaRepository

interface FeedProviderRepository : JpaRepository<FeedProvider, String>

interface FeedFieldMappingRepository : JpaRepository<FeedFieldMapping, java.util.UUID> {
    fun findByProviderId(providerId: String): List<FeedFieldMapping>
}

interface FeedStatusTranslationRepository : JpaRepository<FeedStatusTranslation, java.util.UUID> {
    fun findByProviderId(providerId: String): List<FeedStatusTranslation>
}

interface FeedLeagueMappingRepository : JpaRepository<FeedLeagueMapping, java.util.UUID> {
    fun findByProviderId(providerId: String): List<FeedLeagueMapping>
    fun findByProviderIdAndProviderLeagueId(providerId: String, providerLeagueId: String): FeedLeagueMapping?
}
