package com.bolyartech.forge.server.jetty

import com.bolyartech.forge.server.ForgeServer
import com.bolyartech.forge.server.ForgeSystemServlet
import com.bolyartech.forge.server.WebServer
import com.bolyartech.forge.server.config.ForgeConfigurationException
import com.bolyartech.forge.server.module.SiteModule
import com.bolyartech.forge.server.module.SiteModuleRegisterImpl
import com.bolyartech.forge.server.route.RouteRegisterImpl
import com.mchange.v2.c3p0.ComboPooledDataSource
import jakarta.servlet.MultipartConfigElement
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.session.DatabaseAdaptor
import org.eclipse.jetty.server.session.JDBCSessionDataStore
import org.eclipse.jetty.server.session.JDBCSessionDataStoreFactory
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.slf4j.LoggerFactory
import java.io.File

class WebServerJetty(
    private val forgeConfig: ForgeServer.ConfigurationPack,
    private val dbDataSource: ComboPooledDataSource,
    private val siteModules: List<SiteModule>
) : WebServer {

    private val logger = LoggerFactory.getLogger(this.javaClass)
    private var server: Server? = null


    @Synchronized
    override fun start() {
        val forgeJettyConfiguration = try {
            ForgeJettyConfigurationLoaderFile(forgeConfig.configurationDirectory).load()
        } catch (e: ForgeConfigurationException) {
            logger.error("jetty.conf error: ${e.message}")
            return
        }

        val forgeSystemServlet = ForgeSystemServlet(
            forgeConfig.forgeServerConfiguration.serverNames,
            siteModules,
            SiteModuleRegisterImpl(
                RouteRegisterImpl(
                    forgeConfig.forgeServerConfiguration.isPathInfoEnabled,
                    forgeConfig.forgeServerConfiguration.maxSlashesInPathInfo
                )
            ),
            forceHttps = forgeJettyConfiguration.forceHttps,
            httpsPort = forgeJettyConfiguration.httpsPort
        )

        val dba = DatabaseAdaptor()
        dba.datasource = dbDataSource
        val sessionDataStoreFactory = JDBCSessionDataStoreFactory()
        dba.datasource.connection.use {
            val tableData = JDBCSessionDataStore.SessionTableSchema()
            tableData.schemaName = it.schema
            tableData.catalogName = it.catalog
            sessionDataStoreFactory.setSessionTableSchema(tableData)

            val sql = "SELECT * FROM jettysessions"
            val ps = it.prepareStatement(sql)
            ps.execute()
        }
        sessionDataStoreFactory.setDatabaseAdaptor(dba)

        server = Server()

        setConnectors(server!!, forgeJettyConfiguration)
        server!!.addBean(sessionDataStoreFactory)

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
    override fun stop() {
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