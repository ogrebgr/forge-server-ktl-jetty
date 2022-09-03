package com.bolyartech.forge.server.jetty

import com.bolyartech.forge.server.config.ForgeConfigurationException
import com.bolyartech.forge.server.config.ForgeServerConfiguration
import com.bolyartech.forge.server.config.ForgeServerConfigurationLoaderFile
import com.bolyartech.forge.server.config.detectConfigurationDirectory
import com.bolyartech.forge.server.db.DbConfiguration
import com.bolyartech.forge.server.db.DbConfigurationLoaderFile
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path

data class ConfigurationPack(
    val configurationDirectory: Path,
    val forgeServerConfiguration: ForgeServerConfiguration,
    val forgeJettyConfiguration: ForgeJettyConfiguration,
    val dbConfiguration: DbConfiguration,
)

fun loadConfigurationPack(fs: FileSystem, args: Array<String>): ConfigurationPack {
    val filesystem = FileSystems.getDefault()
    val configDir = detectConfigurationDirectory(filesystem, args)
    if (configDir == null) {
        throw ForgeConfigurationException("Cannot detect the configuration directory. Exiting.")
    }

    val forgeConf = ForgeServerConfigurationLoaderFile(configDir).load()
    val jettyConf = ForgeJettyConfigurationLoaderFile(configDir).load()
    val dbConf = DbConfigurationLoaderFile(configDir).load()

    return ConfigurationPack(configDir, forgeConf, jettyConf, dbConf)
}