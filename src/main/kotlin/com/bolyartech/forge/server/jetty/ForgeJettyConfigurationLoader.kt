package com.bolyartech.forge.server.jetty

import com.bolyartech.forge.server.config.ForgeConfigurationException

interface ForgeJettyConfigurationLoader {
    @Throws(ForgeConfigurationException::class)
    fun load(): ForgeJettyConfiguration
}