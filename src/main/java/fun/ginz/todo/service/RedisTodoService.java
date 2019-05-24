package fun.ginz.todo.service;

import fun.ginz.todo.Contants;
import fun.ginz.todo.entity.Todo;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

public class RedisTodoService implements TodoService {

    private final Vertx vertx;
    private final RedisOptions config;
    private final RedisClient redis;

    public RedisTodoService(Vertx vertx, RedisOptions config) {
        this.vertx = vertx;
        this.config = config;
        this.redis = RedisClient.create(vertx, config);
    }

    @Override
    public Future<Boolean> initData() {
        return this.insert(new Todo(
                Math.abs(new Random().nextInt()),
                "Something to do...",
                false,
                1,
                "todo/ex")).map(Objects::nonNull);
    }

    @Override
    public Future<Todo> insert(Todo todo) {
        Future<Todo> future = Future.future();
        final String encoded = Json.encodePrettily(todo);
        this.redis.hset(Contants.REDIS_TODO_KEY, String.valueOf(todo.getId()), encoded, res -> {
            if (res.succeeded()) {
                future.complete(todo);
            } else {
                future.fail(res.cause());
            }
        });
        return future;
    }

    @Override
    public Future<List<Todo>> getAll() {
        Future<List<Todo>> future = Future.future();
        this.redis.hvals(Contants.REDIS_TODO_KEY, res -> {
            if (res.succeeded()) {
                future.complete(res.result().stream().map(x -> new Todo((String) x)).collect(Collectors.toList()));
            } else {
                future.fail(res.cause());
            }
        });
        return future;
    }

    @Override
    public Future<Optional<Todo>> getCertain(String todoID) {
        Future<Optional<Todo>> future = Future.future();
        this.redis.hget(Contants.REDIS_TODO_KEY, todoID, res -> {
            if (res.succeeded()) {
                future.complete(Optional.ofNullable(res.result() == null ? null : new Todo(res.result())));
            } else {
                future.fail(res.cause());
            }
        });
        return future;
    }

    @Override
    public Future<Todo> update(String todoID, Todo newTodo) {
        return getCertain(todoID).compose(old -> {
            if (old.isPresent()) {
                Todo result = old.get().merge(newTodo);
                return this.insert(result);
            } else {
                return Future.succeededFuture();
            }
        });
    }

    @Override
    public Future<Void> delete(String todoID) {
        Future<Void> future = Future.future();
        this.redis.hdel(Contants.REDIS_TODO_KEY, todoID, res -> {
            if (res.succeeded()) {
                future.complete();
            } else {
                future.fail(res.cause());
            }
        });
        return future;
    }

    @Override
    public Future<Void> deleteAll() {
        Future<Void> future = Future.future();
        this.redis.del(Contants.REDIS_TODO_KEY, res -> {
           if (res.succeeded()) {
               future.complete();
           } else {
               future.fail(res.cause());
           }
        });
        return future;
    }
}
