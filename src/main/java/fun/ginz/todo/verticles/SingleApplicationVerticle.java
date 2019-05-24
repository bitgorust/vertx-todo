package fun.ginz.todo.verticles;

import fun.ginz.todo.Contants;
import fun.ginz.todo.entity.Todo;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class SingleApplicationVerticle extends AbstractVerticle {

    private static final String HTTP_HOST = "0.0.0.0";
    private static final String REDIS_HOST = "redis-host";
    private static final int HTTP_PORT = 8082;
    private static final int REDIS_PORT = 6379;
    private static final String REDIS_TODO_KEY = "VERT_TODO";

    private RedisClient redis;

    private void initData() {
        RedisOptions options = new RedisOptions()
                .setHost(config().getString("redis.host", REDIS_HOST))
                .setPort(config().getInteger("redis.port", REDIS_PORT));

        this.redis = RedisClient.create(vertx, options);

        this.redis.hset(REDIS_TODO_KEY, "24", Json.encodePrettily(
                new Todo(24, "Something to do...", false, 1, "todo/ex")), res -> {
            if (res.failed()) {
                res.cause().printStackTrace();
            }
        });
    }

    private void handleGetOne(RoutingContext context) {
        String todoID = context.request().getParam("todoId");
        if (todoID == null) {
            sendError(400, context.response());
            return;
        }
        redis.hget(REDIS_TODO_KEY, todoID, x -> {
            if (x.succeeded()) {
                String result = x.result();
                if (result == null) {
                    sendError(404, context.response());
                } else {
                    context.response()
                            .putHeader("Content-Type", "application/json")
                            .end(result);
                }
            } else {
                sendError(503, context.response());
            }
        });
    }

    private void handleGetAll(RoutingContext context) {
        redis.hvals(REDIS_TODO_KEY, res -> {
            if (res.succeeded()) {
                String encoded = Json.encodePrettily(
                        res.result().stream().map(x -> new Todo((String) x)).collect(Collectors.toList())
                );
                context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(encoded);
            } else {
                sendError(503, context.response());
            }
        });
    }

    private void handleCreate(RoutingContext context) {
        try {
            Todo todo = wrapObject(new Todo(context.getBodyAsString()), context);
            String encoded = Json.encodePrettily(todo);
            redis.hset(REDIS_TODO_KEY, String.valueOf(todo.getId()), encoded, res -> {
                if (res.succeeded()) {
                    context.response()
                            .setStatusCode(201)
                            .putHeader("Content-Type", "application/json")
                            .end(encoded);
                } else {
                    sendError(503, context.response());
                }
            });
        } catch (DecodeException e) {
            sendError(400, context.response());
        }
    }

    private void handleUpdate(RoutingContext context) {
        try {
            String todoID = context.request().getParam("todoId");
            Todo newTodo = new Todo(context.getBodyAsString());
            if (todoID == null || newTodo == null) {
                sendError(400, context.response());
                return;
            }
            redis.hget(REDIS_TODO_KEY, todoID, x -> {
                if (x.succeeded()) {
                    String result = x.result();
                    if (result == null) {
                        sendError(404, context.response());
                    } else {
                        Todo oldTodo = new Todo(result);
                        String response = Json.encodePrettily(oldTodo.merge(newTodo));
                        redis.hset(REDIS_TODO_KEY, todoID, response, res -> {
                            if (res.succeeded()) {
                                context.response().putHeader("Content-Type", "application/json").end(response);
                            }
                        });
                    }
                } else {
                    sendError(503, context.response());
                }
            });
        } catch (DecodeException e) {
            sendError(400, context.response());
        }
    }

    private void handleDelOne(RoutingContext context) {
        String todoID = context.request().getParam("todoId");
        redis.hdel(REDIS_TODO_KEY, todoID, res -> {
            if (res.succeeded()) {
                context.response().setStatusCode(204).end();
            } else {
                sendError(503, context.response());
            }
        });
    }

    private void handleDelAll(RoutingContext context) {
        redis.del(REDIS_TODO_KEY, res -> {
            if (res.succeeded()) {
                context.response().setStatusCode(204).end();
            } else {
                sendError(503, context.response());
            }
        });
    }

    private Todo wrapObject(Todo todo, RoutingContext context) {
        int id = todo.getId();
        if (id > Todo.getIncId()) {
            Todo.setIncIdWith(id);
        } else if (id == 0) {
            todo.setIncId();
        }
        todo.setUrl(context.request().absoluteURI() + "/" + todo.getId());
        return todo;
    }

    private void sendError(int statusCode, HttpServerResponse response) {
        response.setStatusCode(statusCode).end();
    }

    @Override
    public void start(Future<Void> future) throws Exception {
        initData();

        Set<String> allowHeaders = new HashSet<>();
        allowHeaders.add("X-Requested-With");
        allowHeaders.add("Access-Control-Allow-Origin");
        allowHeaders.add("Origin");
        allowHeaders.add("Content-Type");
        allowHeaders.add("Accept");

        Set<HttpMethod> allowMethods = new HashSet<>();
        allowMethods.add(HttpMethod.GET);
        allowMethods.add(HttpMethod.POST);
        allowMethods.add(HttpMethod.PATCH);
        allowMethods.add(HttpMethod.DELETE);

        Router router = Router.router(vertx);
        router.route().handler(CorsHandler.create("*").allowedHeaders(allowHeaders).allowedMethods(allowMethods));
        router.route().handler(BodyHandler.create());

        router.get(Contants.API_GET_ONE).handler(this::handleGetOne);
        router.get(Contants.API_GET_ALL).handler(this::handleGetAll);
        router.post(Contants.API_CREATE).handler(this::handleCreate);
        router.patch(Contants.API_UPDATE).handler(this::handleUpdate);
        router.delete(Contants.API_DEL_ONE).handler(this::handleDelOne);
        router.delete(Contants.API_DEL_ALL).handler(this::handleDelAll);

        vertx.createHttpServer().requestHandler(router).listen(HTTP_PORT, HTTP_HOST, result -> {
            if (result.succeeded()) {
                future.complete();
            } else {
                future.fail(result.cause());
            }
        });
    }
}
