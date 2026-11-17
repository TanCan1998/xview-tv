package io.github.tancan1998.xviewtv.core.provider

import io.github.tancan1998.xviewtv.core.model.*
import org.junit.Test

class ProviderRegistryTest {
    @Test(expected = ProviderException.Unavailable::class)
    fun accountModeCannotFallBackToGuest() {
        ProviderRegistry(emptyList()).requireProvider(
            ProviderId("missing-account"), AccessMode.ACCOUNT, ProviderCapability.FOLLOWING_TIMELINE,
        )
    }
}
