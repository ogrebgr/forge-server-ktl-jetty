package com.bolyartech.forge.server.jetty

import com.bolyartech.forge.server.ForgeServer
import com.bolyartech.forge.server.ForgeSystemServlet
import com.bolyartech.forge.server.ForgeSystemServlet.Companion.DEFAULT_SESSION_COOKIE_NAME
import com.bolyartech.forge.server.WebServer
import com.bolyartech.forge.server.WebServerInstrumentationReader
import com.bolyartech.forge.server.config.ForgeConfigurationException
import com.bolyartech.forge.server.handler.RouteHandler
import com.bolyartech.forge.server.module.SiteModule
import com.bolyartech.forge.server.module.SiteModuleRegisterImpl
import com.bolyartech.forge.server.route.RouteRegisterImpl
import jakarta.servlet.DispatcherType
import jakarta.servlet.MultipartConfigElement
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.session.DatabaseAdaptor
import org.eclipse.jetty.server.session.JDBCSessionDataStore
import org.eclipse.jetty.server.session.JDBCSessionDataStoreFactory
import org.eclipse.jetty.server.session.SessionDataStoreFactory
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.BlockingArrayQueue
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import javax.sql.DataSource
import org.eclipse.jetty.servlets.CrossOriginFilter


class WebServerJetty(
    private val forgeConfig: ForgeServer.ConfigurationPack,
    private val siteModules: List<SiteModule>,
    private val notFoundHandler: RouteHandler? = null,
    private val internalServerErrorHandler: RouteHandler? = null,
    private val sessionDataStoreFactory: SessionDataStoreFactory? = null,
) : WebServer, WebServerInstrumentationReader {

    private val logger = LoggerFactory.getLogger(this.javaClass)
    private var server: Server? = null
    private var threadPool: QueuedThreadPool? = null

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
            httpsPort = forgeJettyConfiguration.httpsPort,
            notFoundHandler,
            internalServerErrorHandler
        )

        server = if (forgeJettyConfiguration.maxThreads > 0) {
            threadPool = if (forgeJettyConfiguration.minThreads > 0) {
                if (forgeJettyConfiguration.maxThreadPoolQueueSize > 0) {
                    val q = BlockingArrayQueue<Runnable>(forgeJettyConfiguration.maxThreadPoolQueueSize)
                    QueuedThreadPool(forgeJettyConfiguration.maxThreads, forgeJettyConfiguration.minThreads, q)
                } else {
                    QueuedThreadPool(forgeJettyConfiguration.maxThreads, forgeJettyConfiguration.minThreads)
                }
            } else {
                QueuedThreadPool(forgeJettyConfiguration.maxThreads)
            }
            logger.info("Jetty settings: max threads: ${threadPool!!.maxThreads}, min threads: ${threadPool!!.minThreads}, max queue size: ${forgeJettyConfiguration.maxThreadPoolQueueSize}")
            Server(threadPool)
        } else {
            logger.info("Jetty settings: default")
            Server()
        }



        setConnectors(server!!, forgeJettyConfiguration)
        if (sessionDataStoreFactory != null) {
            server!!.addBean(sessionDataStoreFactory)
        }

        val context = ServletContextHandler(ServletContextHandler.SESSIONS)
        context.sessionHandler.maxInactiveInterval = forgeJettyConfiguration.sessionTimeout
        context.maxFormContentSize = forgeJettyConfiguration.maxRequestSize
        context.contextPath = "/"

        if (!forgeConfig.forgeServerConfiguration.accessControlAllowOrigin.isNullOrEmpty()) {
            val cors: FilterHolder = context.addFilter(CrossOriginFilter::class.java, "/*", EnumSet.of(DispatcherType.REQUEST))
            cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, forgeConfig.forgeServerConfiguration.accessControlAllowOrigin)
            cors.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, forgeConfig.forgeServerConfiguration.accessControlAllowOrigin)

            if (!forgeConfig.forgeServerConfiguration.accessControlAllowMethods.isNullOrEmpty()) {
                cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,OPTIONS")
            }

            if (!forgeConfig.forgeServerConfiguration.accessControlAllowHeaders.isNullOrEmpty()) {
                cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "Content-Type,Accept,Origin")
            }
        }

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
        context.sessionHandler.sessionCookieConfig.name = DEFAULT_SESSION_COOKIE_NAME
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

    companion object {
        fun createDbSessionDataStoreFactory(dbDataSource: DataSource) : SessionDataStoreFactory {
            val dba = DatabaseAdaptor()
            dba.datasource = dbDataSource
            val sessionDataStoreFactoryVal = JDBCSessionDataStoreFactory()
            dba.datasource.connection.use {
                val tableData = JDBCSessionDataStore.SessionTableSchema()
                tableData.schemaName = it.schema
                tableData.catalogName = it.catalog
                sessionDataStoreFactoryVal.setSessionTableSchema(tableData)

                val sql = "SELECT * FROM jettysessions"
                val ps = it.prepareStatement(sql)
                ps.execute()
            }
            sessionDataStoreFactoryVal.setDatabaseAdaptor(dba)

            return sessionDataStoreFactoryVal
        }
    }

    override fun getInstrumentation(): WebServerInstrumentationReader {
        return this
    }

    override fun getQueueSize(): Int {
        return threadPool?.queueSize ?: -1
    }

    override fun getReadyThreads(): Int {
        return threadPool?.readyThreads ?: -1
    }

    override fun getUtilizationRate(): Double {
        return threadPool?.utilizationRate ?: -1.0
    }

}