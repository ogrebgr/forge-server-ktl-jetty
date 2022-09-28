package com.bolyartech.forge.server.jetty

data class ForgeJettyConfiguration(
    val host: String,
    val httpPort: Int,
    val httpsPort: Int,
    val forceHttps: Boolean,
    val temporaryDirectory: String,
    val sessionTimeout: Int,
    val maxRequestSize: Int,
    val maxFileUploadSize: Int,
    val fileSizeThreshold: Int,
    val keyStorePath: String,
    val keyStorePassword: String,
)
