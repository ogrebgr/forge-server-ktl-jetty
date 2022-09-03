package com.bolyartech.forge.server.jetty

import jakarta.servlet.MultipartConfigElement
import jakarta.servlet.http.HttpServlet
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.session.SessionDataStoreFactory
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.slf4j.LoggerFactory
import java.io.File

class ForgeJetty(
    private val forgeJettyConfiguration: ForgeJettyConfiguration,
    private val forgeSystemServlet: HttpServlet,
    private val sessionDataStoreFactory: SessionDataStoreFactory?
) {

    private val logger = LoggerFactory.getLogger(this.javaClass)
    private var server: Server? = null


    @Synchronized
    fun start() {
        server = Server()

        setConnectors(server!!, forgeJettyConfiguration)
        if (sessionDataStoreFactory != null) {
            server!!.addBean(sessionDataStoreFactory)
        }

        val context = ServletContextHandler(ServletContextHandler.SESSIONS)
        context.sessionHandler.maxInactiveInterval = forgeJettyConfiguration.sessionTimeout
        context.maxFormContentSize = forgeJettyConfiguration.maxRequestSize
        context.contextPath = "/"

        val holder = ServletHolder(forgeSystemServlet)
        logger.info("Session timeout set to {} seconds", forgeJettyConfiguration.sessionTimeout)
        holder.registration.setMultipartConfig(
            MultipartConfigElement(
                forgeJettyConfiguration.temporaryDirectory,
                forgeJettyConfiguration.maxFileUploadSize.toLong(),
                forgeJettyConfiguration.maxRequestSize.toLong(),
                forgeJettyConfiguration.fileSizeThreshold
            )
        )
        context.addServlet(holder, "/*")
        server!!.handler = context
        try {
            server!!.start()
            server!!.join()
        } catch (e: Exception) {
            logger.error("Error starting the server: ", e)
            try {
                server!!.stop()
            } catch (e: Exception) {
                //suppress
            }
        }
    }

    @Synchronized
    fun stop() {
        if (server != null) {
            try {
                server!!.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        server = null
    }

    private fun setConnectors(server: Server, conf: ForgeJettyConfiguration) {
        val connectors: MutableList<Connector> = ArrayList()
        if (conf.httpPort > 0) {
            val connector = ServerConnector(this.server)
            connector.host = conf.host
            connector.port = conf.httpPort
            connectors.add(connector)
            logger.info("Listening HTTP on {}, port {}", conf.host, conf.httpPort)
        }
        if (conf.httpsPort > 0) {
            val f = File(conf.keyStorePath)
            if (!f.exists()) {
                logger.error("Cannot find SSL keystore file at: " + conf.keyStorePath)
                throw IllegalStateException("Cannot find SSL keystore file at: " + conf.keyStorePath)
            }
            val sslContextFactory = SslContextFactory.Server()
            sslContextFactory.keyStorePath = conf.keyStorePath
            if (!conf.keyStorePassword.isEmpty()) {
                sslContextFactory.setKeyStorePassword(conf.keyStorePassword)
            }
            val connector = ServerConnector(server, sslContextFactory)
            connector.host = conf.host
            connector.port = conf.httpsPort
            connectors.add(connector)
            logger.info("Listening HTTPS on {}, port {}", conf.host, conf.httpsPort)
        }

        this.server!!.connectors = connectors.toTypedArray()
    }
}