package fun.ginz.todo.verticles;

import fun.ginz.todo.Contants;
import fun.ginz.todo.entity.Todo;
import fun.ginz.todo.service.RedisTodoService;
import fun.ginz.todo.service.TodoService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.redis.RedisOptions;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class TodoVerticle extends AbstractVerticle {

    private static final String HOST = "0.0.0.0";
    private static final Integer PORT = 8082;

    private TodoService todoService;

    private void initData() {
        String serviceType = config().getString("service.type", "redis");
        switch (serviceType) {
            case "jdbc":
//                todoService = new JdbcTodoService(vertx, config());
                break;
            case "redis":
            default:
                RedisOptions options = new RedisOptions()
                        .setHost(config().getString("redis.host", "127.0.0.1"))
                        .setPort(config().getInteger("redis.port", 6379));
                todoService = new RedisTodoService(vertx, options);
        }

        todoService.initData().setHandler(res -> {
            if (res.failed()) {
                res.cause().printStackTrace();
            }
        });
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
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
        router.route().handler(BodyHandler.create());
        router.route().handler(CorsHandler.create("*").allowedHeaders(allowHeaders).allowedMethods(allowMethods));

        router.get(Contants.API_GET_ONE).handler(this::handleGetOne);
        router.get(Contants.API_GET_ALL).handler(this::handleGetAll);
        router.post(Contants.API_CREATE).handler(this::handleCreate);
        router.patch(Contants.API_UPDATE).handler(this::handleUpdate);
        router.delete(Contants.API_DEL_ONE).handler(this::handleDelOne);
        router.delete(Contants.API_DEL_ALL).handler(this::handleDelAll);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(config().getInteger("http.port", PORT),
                        config().getString("http.host", HOST), result -> {
                            if (result.succeeded()) {
                                startFuture.complete();
                            } else {
                                startFuture.fail(result.cause());
                            }
                        });
    }


    private <T> Handler<AsyncResult<T>> resultHandler(RoutingContext context, Consumer<T> consumer) {
        return res -> {
            if (res.succeeded()) {
                consumer.accept(res.result());
            } else {
                serviceUnavailable(context);
            }
        };
    }

    private void handleGetOne(RoutingContext routingContext) {
        String todoID = routingContext.request().getParam("todoId");
        if (todoID == null) {
            badRequest(routingContext);
            return;
        }

        todoService.getCertain(todoID).setHandler(resultHandler(routingContext, todo -> {
            if (!todo.isPresent()) {
                notFound(routingContext);
            } else {
                routingContext.response()
                        .putHeader("Content-Type", "application/json")
                        .end(Json.encodePrettily(todo.get()));
            }
        }));
    }

    private void handleGetAll(RoutingContext routingContext) {
        todoService.getAll().setHandler(resultHandler(routingContext, list -> routingContext.response()
                .putHeader("Content-Type", "application/json")
                .end(Json.encodePrettily(list))));
    }

    private void handleCreate(RoutingContext routingContext) {
        try {
            Todo todo = wrapObject(new Todo(routingContext.getBodyAsString()), routingContext);
            todoService.insert(todo).setHandler(resultHandler(routingContext, result -> {
                routingContext.response()
                        .setStatusCode(201)
                        .putHeader("Content-Type", "application/json")
                        .end(Json.encodePrettily(result));
            }));
        } catch (DecodeException e) {
            badRequest(routingContext);
        }
    }

    private void handleUpdate(RoutingContext routingContext) {
        try {
            String todoID = routingContext.request().getParam("todoId");
            if (todoID == null) {
                badRequest(routingContext);
                return;
            }
            Todo newTodo = new Todo(routingContext.getBodyAsString());
            todoService.update(todoID, newTodo).setHandler(resultHandler(routingContext, todo -> {
                if (todo == null) {
                    notFound(routingContext);
                } else {
                    routingContext.response()
                            .putHeader("Content-Type", "application/json")
                            .end(Json.encodePrettily(todo));
                }
            }));
        } catch (DecodeException e) {
            badRequest(routingContext);
        }
    }

    private Handler<AsyncResult<Void>> deleteHandler(RoutingContext context) {
        return res -> {
            if (res.succeeded()) {
                context.response().setStatusCode(204).end();
            } else {
                serviceUnavailable(context);
            }
        };
    }

    private void handleDelOne(RoutingContext routingContext) {
        String todoID = routingContext.request().getParam("todoId");
        todoService.delete(todoID).setHandler(deleteHandler(routingContext));

    }

    private void handleDelAll(RoutingContext routingContext) {
        todoService.deleteAll().setHandler(deleteHandler(routingContext));
    }

    private void sendError(int statusCode, HttpServerResponse response) {
        response.setStatusCode(statusCode).end();
    }

    private void badRequest(RoutingContext context) {
        context.response().setStatusCode(400).end();
    }

    private void notFound(RoutingContext context) {
        context.response().setStatusCode(404).end();
    }

    private void serviceUnavailable(RoutingContext context) {
        context.response().setStatusCode(503).end();
    }

    private Todo wrapObject(Todo todo, RoutingContext context) {
        int id = todo.getId();
        if (id > Todo.getIncId()) {
            Todo.setIncIdWith(id);
        } else if (id == 0)
            todo.setIncId();
        todo.setUrl(context.request().absoluteURI() + "/" + todo.getId());
        return todo;
    }
}
