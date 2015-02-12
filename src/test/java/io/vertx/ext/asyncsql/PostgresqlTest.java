package io.vertx.ext.asyncsql;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="http://www.campudus.com">Joern Bernhardt</a>.
 */
public class PostgresqlTest extends VertxTestBase {
  private static final Logger log = LoggerFactory.getLogger(VertxTestBase.class);

  AsyncSqlService asyncSqlService;

  final String address = "campudus.postgresql";

  final JsonObject config = new JsonObject().put("postgresql", new JsonObject().put("address", address));

  @Override
  public void setUp() throws Exception {
    super.setUp();
    asyncSqlService = AsyncSqlService.createPostgreSqlService(vertx, config);

    CountDownLatch latch = new CountDownLatch(1);

    asyncSqlService.start(onSuccess(x -> {
      log.info("in onSuccess of start asyncSqlService");
      latch.countDown();
    }));

    awaitLatch(latch);
  }

  @Test
  public void someTest() throws Exception {
    log.info("before getConnection");
    asyncSqlService.getConnection(onSuccess(conn -> {
      log.info("in getConnection");
      conn.query("SELECT 1 AS something", onSuccess(resultSet -> {
        assertNotNull(resultSet);
        assertNotNull(resultSet.getColumnNames());
        assertNotNull(resultSet.getResults());
        assertEquals(new JsonArray().add(1), resultSet.getResults().get(0));
        testComplete();
      }));
    }));

    await();
  }

}
