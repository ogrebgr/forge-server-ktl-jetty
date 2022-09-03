package com.bolyartech.forge.server.jetty

import com.bolyartech.forge.server.config.ForgeConfigurationException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.pathString

class ForgeJettyConfigurationLoaderFile(private val configDirPath: Path) : ForgeJettyConfigurationLoader {
    companion object {
        private const val JETTY_CONF_FILE = "jetty.conf"

        private const val PROP_SERVER_NAMES = "server_names"
        private const val PROP_HOST = "host"
        private const val PROP_HTTP_PORT = "http_port"
        private const val PROP_HTTPS_PORT = "https_port"
        private const val PROP_SESSION_TIMEOUT = "session_timeout_seconds"
        private const val PROP_TEMPORARY_DIRECTORY = "temporary_directory"
        private const val PROP_MAX_REQUEST_SIZE = "max_file_upload_size_bytes"
        private const val PROP_MAX_FILE_UPLOAD_SIZE = "max_request_size_bytes"
        private const val PROP_FILE_THRESHOLD_SIZE = "file_size_threshold"
        private const val PROP_KEYSTORE_PATH = "keystore_path"
        private const val PROP_KEYSTORE_PASSWORD = "keystore_password"
        private const val PROP_TRUSTSTORE_PATH = "truststore_path"
        private const val PROP_TRUSTSTORE_PASSWORD = "truststore_password"
    }

    override fun load(): ForgeJettyConfiguration {
        val path = Path.of(configDirPath.pathString, JETTY_CONF_FILE)
        if (!path.exists()) {
            throw ForgeConfigurationException("Cannot find jetty configuration file (${path.pathString})")
        }

        val prop = Properties()
        Files.newInputStream(path).use {
            prop.load(it)
        }

        val serverNamesTmp = prop.getProperty(PROP_SERVER_NAMES)

        val serverNames = if (serverNamesTmp != null && serverNamesTmp.trim().isNotEmpty()) {
            serverNamesTmp.split(",").map { it.trim() }
        } else {
            emptyList<String>()
        }


        return ForgeJettyConfiguration(
            serverNames,
            prop.getProperty(PROP_HOST),
            prop.getProperty(PROP_HTTP_PORT).toInt(),
            prop.getProperty(PROP_HTTPS_PORT).toInt(),
            prop.getProperty(PROP_TEMPORARY_DIRECTORY),
            prop.getProperty(PROP_SESSION_TIMEOUT).toInt(),
            prop.getProperty(PROP_MAX_REQUEST_SIZE).toInt(),
            prop.getProperty(PROP_MAX_FILE_UPLOAD_SIZE).toInt(),
            prop.getProperty(PROP_FILE_THRESHOLD_SIZE).toInt(),
            prop.getProperty(PROP_KEYSTORE_PATH),
            prop.getProperty(PROP_KEYSTORE_PASSWORD),
        )
    }
}