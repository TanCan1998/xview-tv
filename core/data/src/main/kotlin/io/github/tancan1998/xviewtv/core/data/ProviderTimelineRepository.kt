package io.github.tancan1998.xviewtv.core.data

import io.github.tancan1998.xviewtv.core.domain.TimelineRepository
import io.github.tancan1998.xviewtv.core.model.*
import io.github.tancan1998.xviewtv.core.provider.*

class ProviderTimelineRepository(
    private val registry: ProviderRegistry,
    private val providerId: ProviderId,
    private val mode: AccessMode,
    private val capability: ProviderCapability,
) : TimelineRepository {
    override suspend fun getTimeline(request: TimelineRequest): Page<Post> {
        val provider = registry.requireProvider(providerId, mode, capability)
        if (mode == AccessMode.ACCOUNT && provider.getCurrentAccount() == null) {
            throw ProviderException.AuthenticationRequired()
        }
        return provider.getTimeline(request)
    }
}
