package io.github.tancan1998.xviewtv.core.provider

import io.github.tancan1998.xviewtv.core.model.*

class ProviderRegistry(providers: List<ContentProvider>) {
    private val entries = providers.associateBy { it.id }
    init { require(entries.size == providers.size) { "Provider IDs must be unique" } }

    fun requireProvider(
        id: ProviderId,
        mode: AccessMode,
        capability: ProviderCapability,
    ): ContentProvider {
        val provider = entries[id] ?: throw ProviderException.Unavailable()
        if (provider.mode != mode) throw ProviderException.Unavailable()
        if (capability !in provider.capabilities) throw ProviderException.Unsupported(capability)
        return provider
    }
}
