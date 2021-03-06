package io.vertx.ext.asyncsql

/**
 * @author <a href="http://www.campudus.com">Joern Bernhardt</a>.
 */
class MySQLScalaTest extends DirectTestBase with MySQLConfig {

  override lazy val asyncSqlClient = MySQLClient.createNonShared(vertx, config)

}
