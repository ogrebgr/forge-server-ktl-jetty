package com.bolyartech.forge.server.jetty

data class ForgeJettyConfiguration(
    val serverNames: List<String>,
    val host: String,
    val httpPort: Int,
    val httpsPort: Int,
    val temporaryDirectory: String,
    val sessionTimeout: Int,
    val maxRequestSize: Int,
    val maxFileUploadSize: Int,
    val fileSizeThreshold: Int,
    val keyStorePath: String,
    val keyStorePassword: String,
)
