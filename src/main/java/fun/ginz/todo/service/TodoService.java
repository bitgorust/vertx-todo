package fun.ginz.todo.service;

import fun.ginz.todo.entity.Todo;
import io.vertx.core.Future;

import java.util.List;
import java.util.Optional;

public interface TodoService {

    Future<Boolean> initData();

    Future<Todo> insert(Todo todo);

    Future<List<Todo>> getAll();

    Future<Optional<Todo>> getCertain(String todoID);

    Future<Todo> update(String todoID, Todo newTodo);

    Future<Void> delete(String todoID);

    Future<Void> deleteAll();

}
