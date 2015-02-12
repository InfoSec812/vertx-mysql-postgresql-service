/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.groovy.ext.asyncsql;
import groovy.transform.CompileStatic
import io.vertx.lang.groovy.InternalHelper
import io.vertx.groovy.ext.sql.SqlConnection
import io.vertx.groovy.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
/**
 *
 * Represents an asynchronous MySQL or PostgreSQL service.
 *
 * @author <a href="http://www.campudus.com">Joern Bernhardt</a>.
 */
@CompileStatic
public class AsyncSqlService {
  final def io.vertx.ext.asyncsql.AsyncSqlService delegate;
  public AsyncSqlService(io.vertx.ext.asyncsql.AsyncSqlService delegate) {
    this.delegate = delegate;
  }
  public Object getDelegate() {
    return delegate;
  }
  /**
   * Create a MySQL service
   *
   * @param vertx  the Vert.x instance
   * @param config  the config
   * @return the service
   */
  public static AsyncSqlService createMySqlService(Vertx vertx, Map<String, Object> config) {
    def ret= AsyncSqlService.FACTORY.apply(io.vertx.ext.asyncsql.AsyncSqlService.createMySqlService((io.vertx.core.Vertx)vertx.getDelegate(), config != null ? new io.vertx.core.json.JsonObject(config) : null));
    return ret;
  }
  /**
   * Create a PostgreSQL service
   *
   * @param vertx  the Vert.x instance
   * @param config  the config
   * @return the service
   */
  public static AsyncSqlService createPostgreSqlService(Vertx vertx, Map<String, Object> config) {
    def ret= AsyncSqlService.FACTORY.apply(io.vertx.ext.asyncsql.AsyncSqlService.createPostgreSqlService((io.vertx.core.Vertx)vertx.getDelegate(), config != null ? new io.vertx.core.json.JsonObject(config) : null));
    return ret;
  }
  /**
   * Create an event bus proxy to a service which lives somewhere on the network and is listening on the specified
   * event bus address
   *
   * @param vertx  the Vert.x instance
   * @param address  the address on the event bus where the service is listening
   * @return
   */
  public static AsyncSqlService createEventBusProxy(Vertx vertx, String address) {
    def ret= AsyncSqlService.FACTORY.apply(io.vertx.ext.asyncsql.AsyncSqlService.createEventBusProxy((io.vertx.core.Vertx)vertx.getDelegate(), address));
    return ret;
  }
  /**
   * Called to start the service
   */
  public void start(Handler<AsyncResult<Void>> whenDone) {
    this.delegate.start(whenDone);
  }
  /**
   * Called to stop the service
   */
  public void stop(Handler<AsyncResult<Void>> whenDone) {
    this.delegate.stop(whenDone);
  }
  /**
   * Returns a connection that can be used to perform SQL operations on. It's important to remember to close the
   * connection when you are done, so it is returned to the pool.
   *
   * @param handler the handler which is called when the <code>JdbcConnection</code> object is ready for use.
   */
  public void getConnection(Handler<AsyncResult<SqlConnection>> handler) {
    this.delegate.getConnection(new Handler<AsyncResult<io.vertx.ext.sql.SqlConnection>>() {
      public void handle(AsyncResult<io.vertx.ext.sql.SqlConnection> event) {
        AsyncResult<SqlConnection> f
        if (event.succeeded()) {
          f = InternalHelper.<SqlConnection>result(new SqlConnection(event.result()))
        } else {
          f = InternalHelper.<SqlConnection>failure(event.cause())
        }
        handler.handle(f)
      }
    });
  }

  static final java.util.function.Function<io.vertx.ext.asyncsql.AsyncSqlService, AsyncSqlService> FACTORY = io.vertx.lang.groovy.Factories.createFactory() {
    io.vertx.ext.asyncsql.AsyncSqlService arg -> new AsyncSqlService(arg);
  };
}
