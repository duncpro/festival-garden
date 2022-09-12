package com.duncpro.festivalgarden.local

import com.duncpro.jackal.SQLDatabase
import com.duncpro.jackal.compileSQLScript
import com.duncpro.jackal.executeTransaction
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.h2.tools.Server
import org.slf4j.LoggerFactory
import java.io.File
import java.net.BindException

private val logger = LoggerFactory.getLogger("com.duncpro.festivalgarden.local.setupLocalDatabase")

suspend fun setupLocalDatabase(): Pair<Server, SQLDatabase> {
    val h2DatabaseServer = Server.createTcpServer( "-tcpPort", "9123", "-tcpAllowOthers")
    try {
        h2DatabaseServer.start()
    } catch (e: BindException) {
        logger.error("Failed to launch database server because the socket was already taken." +
                " (Maybe the server is alredy started?)", e)

    }

    val dataSource = org.h2.jdbcx.JdbcDataSource()
    val executors = java.util.concurrent.Executors.newCachedThreadPool()
    dataSource.setURL(System.getenv("LOCAL_DATABASE_URL"))
    val database = com.duncpro.jackal.jdbc.DataSourceWrapper(executors, dataSource)
    val initScriptFilePath = File("./../init-database.sql")
    database.executeTransaction {
        compileSQLScript(initScriptFilePath.inputStream())
            .onEach { it.executeUpdate() }
            .collect()
        commit()
    }
    return Pair(h2DatabaseServer, database)
}