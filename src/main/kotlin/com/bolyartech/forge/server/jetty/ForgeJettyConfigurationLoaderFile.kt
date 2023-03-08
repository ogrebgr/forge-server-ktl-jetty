package com.bolyartech.forge.server.jetty

import com.bolyartech.forge.server.config.ForgeConfigurationException
import com.bolyartech.forge.server.config.ForgeServerConfiguration.Companion.extractIntValue0Positive
import com.bolyartech.forge.server.config.ForgeServerConfiguration.Companion.extractIntValuePositive
import com.bolyartech.forge.server.config.ForgeServerConfiguration.Companion.extractStringValue
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.pathString

class ForgeJettyConfigurationLoaderFile(private val configDirPath: Path) : ForgeJettyConfigurationLoader {
    companion object {
        private const val JETTY_CONF_FILE = "jetty.conf"

        private const val PROP_HOST = "host"
        private const val PROP_HTTP_PORT = "http_port"
        private const val PROP_HTTPS_PORT = "https_port"
        private const val PROP_FORCE_HTTPS = "force_https"
        private const val PROP_SESSION_TIMEOUT = "session_timeout_seconds"
        private const val PROP_TEMPORARY_DIRECTORY = "temporary_directory"
        private const val PROP_MAX_REQUEST_SIZE = "max_file_upload_size_bytes"
        private const val PROP_MAX_FILE_UPLOAD_SIZE = "max_request_size_bytes"
        private const val PROP_FILE_THRESHOLD_SIZE = "file_size_threshold"
        private const val PROP_KEYSTORE_PATH = "keystore_path"
        private const val PROP_KEYSTORE_PASSWORD = "keystore_password"
        private const val PROP_MAX_THREADS = "max_threads"
        private const val PROP_MIN_THREADS = "min_threads"
        private const val PROP_MAX_THREAD_POOL_QUEUE_SIZE = "max_thread_pool_queue_size"
//        private const val PROP_TRUSTSTORE_PATH = "truststore_path"
//        private const val PROP_TRUSTSTORE_PASSWORD = "truststore_password"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Throws(ForgeConfigurationException::class)
    override fun load(): ForgeJettyConfiguration {
        val path = Path.of(configDirPath.pathString, JETTY_CONF_FILE)
        if (!path.exists()) {
            throw ForgeConfigurationException("Cannot find jetty configuration file (${path.pathString})")
        }

        val prop = Properties()
        Files.newInputStream(path).use {
            prop.load(it)
        }


        val forceHttps = prop.getProperty(PROP_FORCE_HTTPS)
        val forceHttpsFinal = if (!forceHttps.isNullOrEmpty()) {
            forceHttps.toBoolean()
        } else {
            false
        }

        return ForgeJettyConfiguration(
            prop.getProperty(PROP_HOST),
            extractIntValuePositive(prop, PROP_HTTP_PORT),
            extractIntValue0Positive(prop, PROP_HTTPS_PORT),
            forceHttpsFinal,
            extractStringValue(prop, PROP_TEMPORARY_DIRECTORY),
            extractIntValuePositive(prop, PROP_SESSION_TIMEOUT),
            extractIntValuePositive(prop, PROP_MAX_REQUEST_SIZE),
            extractIntValuePositive(prop, PROP_MAX_FILE_UPLOAD_SIZE),
            extractIntValue0Positive(prop, PROP_FILE_THRESHOLD_SIZE),
            extractStringValue(prop, PROP_KEYSTORE_PATH),
            extractStringValue(prop, PROP_KEYSTORE_PASSWORD),
            extractIntValue0Positive(prop, PROP_MAX_THREADS, 0),
            extractIntValue0Positive(prop, PROP_MIN_THREADS, 0),
            extractIntValue0Positive(prop, PROP_MAX_THREAD_POOL_QUEUE_SIZE, 0),
        )
    }
}