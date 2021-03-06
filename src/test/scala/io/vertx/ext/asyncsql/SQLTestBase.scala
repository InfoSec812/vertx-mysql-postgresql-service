package io.vertx.ext.asyncsql

import io.vertx.core.json.{JsonArray, JsonObject}
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.core.{AsyncResult, Handler}
import io.vertx.ext.asyncsql.impl.pool.SimpleExecutionContext
import io.vertx.ext.sql.SQLConnection
import io.vertx.test.core.VertxTestBase
import org.junit.Test

import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * @author <a href="http://www.campudus.com">Joern Bernhardt</a>.
 */
abstract class SQLTestBase extends VertxTestBase with TestData {

  protected val log: Logger = LoggerFactory.getLogger(super.getClass)
  implicit val executionContext: ExecutionContext = SimpleExecutionContext.apply(log)

  import scala.collection.JavaConverters._

  def config: JsonObject

  def asyncSqlClient: AsyncSQLClient

  @Test
  def simpleConnection(): Unit = completeTest {
    for {
      conn <- arhToFuture(asyncSqlClient.getConnection _)
      res <- arhToFuture((conn.query _).curried("SELECT 1 AS something"))
    } yield {
      assertNotNull(res)
      val expected = new JsonObject()
        .put("columnNames", new JsonArray().add("something"))
        .put("results", new JsonArray().add(new JsonArray().add(1)))
      assertEquals(expected, res.toJson)
      conn
    }
  }

  @Test
  def simpleSelect(): Unit = completeTest {
    for {
      conn <- arhToFuture(asyncSqlClient.getConnection _)
      _ <- setupSimpleTestTable(conn)
      res <- arhToFuture((conn.queryWithParams _).curried("SELECT name FROM test_table WHERE id=?")(new JsonArray().add(2)))
    } yield {
      assertNotNull(res)
      assertEquals(List("name"), res.getColumnNames.asScala)
      assertEquals(names(2), res.getResults.get(0).getString(0))
      conn
    }
  }

  @Test
  def makeTwoTransactionsAfterEachOther(): Unit = completeTest {
    for {
      c <- arhToFuture(asyncSqlClient.getConnection _)
      _ <- arhToFuture((c.setAutoCommit _).curried(false))
      one <- arhToFuture((c.query _).curried("SELECT 1"))
      _ <- arhToFuture(c.commit _)
      two <- arhToFuture((c.query _).curried("SELECT 2"))
      _ <- arhToFuture(c.commit _)
      _ <- arhToFuture((c.setAutoCommit _).curried(true))
    } yield c
  }

  @Test
  def updateRow(): Unit = completeTest {
    val id = 0
    val name = "Adele"
    for {
      conn <- arhToFuture(asyncSqlClient.getConnection _)
      _ <- setupSimpleTestTable(conn)
      updateRes <- arhToFuture((conn.updateWithParams _).curried("UPDATE test_table SET name=? WHERE id=?")(new JsonArray().add(name).add(id)))
      selectRes <- arhToFuture((conn.query _).curried("SELECT name FROM test_table ORDER BY id"))
    } yield {
      assertNotNull(updateRes)
      assertNotNull(selectRes)
      assertEquals(1, updateRes.getUpdated)
      assertEquals(names.map(n => new JsonArray().add(if (n == "Albert") name else n)), selectRes.getResults.asScala)
      conn
    }
  }

  @Test
  def rollingBack(): Unit = completeTest {
    val id = 0
    val name = "Adele"

    for {
      conn <- arhToFuture(asyncSqlClient.getConnection _)
      _ <- setupSimpleTestTable(conn)
      _ <- arhToFuture((conn.setAutoCommit _).curried(false))
      updateRes <- arhToFuture((conn.updateWithParams _).curried("UPDATE test_table SET name=? WHERE id=?")(new JsonArray().add(name).add(id)))
      selectRes <- arhToFuture((conn.query _).curried("SELECT name FROM test_table ORDER BY id"))
      _ <- Future.successful {
        assertNotNull(updateRes)
        assertNotNull(selectRes)
        assertEquals(1, updateRes.getUpdated)
        assertEquals(names.map(n => new JsonArray().add(if (n == names(id)) name else n)), selectRes.getResults.asScala)
      }
      _ <- arhToFuture(conn.rollback _)
      res <- arhToFuture((conn.query _).curried("SELECT name FROM test_table ORDER BY id"))
    } yield {
      assertNotNull(res)
      assertEquals(names.map(n => new JsonArray().add(n)), res.getResults.asScala)
      conn
    }
  }

  @Test
  def multipleConnections(): Unit = completeTest {
    val id = 0
    val name = "Adele"

    for {
      conn <- arhToFuture(asyncSqlClient.getConnection _)
      _ <- setupSimpleTestTable(conn)
      c1 <- arhToFuture(asyncSqlClient.getConnection _)
      c2 <- arhToFuture(asyncSqlClient.getConnection _)
      _ <- arhToFuture((c1.setAutoCommit _).curried(false))
      c1Update <- arhToFuture((c1.updateWithParams _).curried("UPDATE test_table SET name=? WHERE id=?")(new JsonArray().add(name).add(id)))
      c2Select <- arhToFuture((c2.query _).curried("SELECT name FROM test_table ORDER BY id"))
      _ <- arhToFuture(c1.rollback _)
      _ <- arhToFuture(c1.close _)
      _ <- arhToFuture(c2.close _)
    } yield {
      assertEquals(1, c1Update.getUpdated)
      assertEquals(names.map(n => new JsonArray().add(n)), c2Select.getResults.asScala)
      conn
    }
  }

  @Test
  def useSetAutoCommitWhileInTransaction(): Unit = completeTest {
    val id = 0
    val name = "Adele"

    for {
      conn <- arhToFuture(asyncSqlClient.getConnection _)
      _ <- setupSimpleTestTable(conn)
      c1 <- arhToFuture(asyncSqlClient.getConnection _)
      c2 <- arhToFuture(asyncSqlClient.getConnection _)
      _ <- arhToFuture((c1.setAutoCommit _).curried(false))
      c1Update <- arhToFuture((c1.updateWithParams _).curried("UPDATE test_table SET name=? WHERE id=?")(new JsonArray().add(name).add(id)))
      _ <- arhToFuture((c1.setAutoCommit _).curried(true))
      c2Select <- arhToFuture((c2.query _).curried("SELECT name FROM test_table ORDER BY id"))
      _ <- arhToFuture(c1.close _)
      _ <- arhToFuture(c2.close _)
    } yield {
      assertEquals(1, c1Update.getUpdated)
      assertEquals(name, c2Select.getResults.asScala.head.getString(0))
      conn
    }
  }

  @Test
  def rollingBackWhenNotInTransaction(): Unit = completeTest {
    val id = 0
    val name = "Adele"

    for {
      conn <- arhToFuture(asyncSqlClient.getConnection _)
      _ <- setupSimpleTestTable(conn)
      _ <- arhToFuture((conn.setAutoCommit _).curried(false))
      _ <- arhToFuture((conn.updateWithParams _).curried("UPDATE test_table SET name=? WHERE id=?")(new JsonArray().add(name).add(id)))
      _ <- arhToFuture((conn.setAutoCommit _).curried(true))
      rollbackResult <- arhToFuture(conn.rollback _) map { sth =>
        fail("Should get an exception when rolling back with autocommit = true")
        sth
      } recoverWith {
        case ex: Throwable => Future.successful("rolled back with error")
      }
    } yield {
      assertEquals("rolled back with error", rollbackResult)
      conn
    }
  }

  @Test
  def commitWhenNotInTransaction(): Unit = completeTest {
    val id = 0
    val name = "Adele"

    for {
      conn <- arhToFuture(asyncSqlClient.getConnection _)
      _ <- setupSimpleTestTable(conn)
      _ <- arhToFuture((conn.setAutoCommit _).curried(false))
      _ <- arhToFuture((conn.updateWithParams _).curried("UPDATE test_table SET name=? WHERE id=?")(new JsonArray().add(name).add(id)))
      _ <- arhToFuture((conn.setAutoCommit _).curried(true))
      commitResult <- arhToFuture(conn.commit _) map { sth =>
        fail("Should get an exception when commit() with autocommit = true")
        sth
      } recoverWith {
        case ex: Throwable => Future.successful("commit with error")
      }
    } yield {
      assertEquals("commit with error", commitResult)
      conn
    }
  }

  @Test
  def insert(): Unit = completeTest {
    val id = 27L
    val name = "Adele"

    for {
      c <- arhToFuture(asyncSqlClient.getConnection _)
      _ <- setupSimpleTestTable(c)
      i <- arhToFuture((c.updateWithParams _).curried("INSERT INTO test_table (id, name) VALUES (?, ?)")(new JsonArray().add(id).add(name)))
      s <- arhToFuture((c.query _).curried("SELECT id, name FROM test_table ORDER BY id"))
    } yield {
      val results = s.getResults
      val fields = s.getColumnNames.asScala
      assertEquals(List("id", "name"), fields)
      assertEquals(names.zipWithIndex.map(_.swap) ++ List((id, name)), results.asScala.map { arr =>
        (arr.getLong(0), arr.getString(1))
      }.toList)
      c
    }
  }

  @Test
  def selectNullValues(): Unit = completeTest {
    for {
      c <- arhToFuture(asyncSqlClient.getConnection _)
      _ <- arhToFuture((c.execute _).curried("DROP TABLE IF EXISTS test_nulls_table"))
      _ <- arhToFuture((c.execute _).curried(
        """CREATE TABLE test_nulls_table (
          |  id BIGINT,
          |  name VARCHAR(255) NULL
          |)""".stripMargin))
      i <- arhToFuture((c.update _).curried("INSERT INTO test_nulls_table (id, name) VALUES (1, NULL)"))
      s <- arhToFuture((c.query _).curried("SELECT id, name FROM test_nulls_table ORDER BY id"))
    } yield {
      val results = s.getResults
      val fields = s.getColumnNames.asScala
      assertEquals(List("id", "name"), fields)
      assertEquals(List((1, null)), results.asScala.map { arr =>
        (arr.getLong(0), arr.getString(1))
      }.toList)
      c
    }
  }

  protected def insertedTime1 = "2015-02-22T07:15:01.234Z"

  protected def expectedTime1 = "2015-02-22T07:15:01.234"

  protected def insertedTime2 = "2014-06-27T17:50:02.468+02:00"

  protected def expectedTime2 = "2014-06-27T17:50:02.468"

  @Test
  def selectDateValues(): Unit = completeTest {
    for {
      c <- arhToFuture(asyncSqlClient.getConnection _)
      _ <- arhToFuture((c.execute _).curried("DROP TABLE IF EXISTS test_date_table"))
      _ <- arhToFuture((c.execute _).curried(
        """CREATE TABLE test_date_table (
          |  id BIGINT,
          |  some_date DATE,
          |  some_timestamp TIMESTAMP
          |)""".stripMargin))
      _ <- arhToFuture((c.updateWithParams _).curried(s"INSERT INTO test_date_table (id, some_date, some_timestamp) VALUES (?, ?, ?)")(new JsonArray().add(1).add("2015-02-22").add(insertedTime1)))
      _ <- arhToFuture((c.updateWithParams _).curried(s"INSERT INTO test_date_table (id, some_date, some_timestamp) VALUES (?, ?, ?)")(new JsonArray().add(2).add("2007-07-20").add(insertedTime2)))
      s <- arhToFuture((c.query _).curried("SELECT id, some_date, some_timestamp FROM test_date_table ORDER BY id"))
    } yield {
      val results = s.getResults
      val fields = s.getColumnNames.asScala
      assertEquals(List("id", "some_date", "some_timestamp"), fields)
      assertEquals(List(
        (1, "2015-02-22", expectedTime1),
        (2, "2007-07-20", expectedTime2)
      ), results.asScala.map(arr => (arr.getLong(0), arr.getString(1), arr.getString(2))).toList)
      c
    }
  }

  protected def arhToFuture[T](fn: Handler[AsyncResult[T]] => _): Future[T] = {
    val p = Promise[T]()
    fn(new Handler[AsyncResult[T]] {
      override def handle(event: AsyncResult[T]): Unit =
        if (event.succeeded()) p.success(event.result()) else p.failure(event.cause())
    })
    p.future
  }

  private def completeTest(f: Future[SQLConnection]): Unit = {
    (for {
      conn <- f
      _ <- arhToFuture(conn.close _)
    } yield {
      testComplete()
    }) recover {
      case ex =>
        log.error("should not get this exception", ex)
        fail("got exception")
    }

    await()
  }

  private def setupSimpleTestTable(conn: SQLConnection): Future[SQLConnection] = {
    for {
      _ <- arhToFuture((conn.execute _).curried("BEGIN"))
      _ <- arhToFuture((conn.execute _).curried("DROP TABLE IF EXISTS test_table"))
      _ <- arhToFuture((conn.execute _).curried(
        """CREATE TABLE test_table (
          |  id BIGINT,
          |  name VARCHAR(255)
          |)""".stripMargin))
      _ <- arhToFuture((conn.update _).curried(s"INSERT INTO test_table (id, name) VALUES ${
        simpleTestTable.mkString(",")
      }"))
      _ <- arhToFuture((conn.execute _).curried("COMMIT"))
    } yield conn
  }

}
