package fun.ginz.todo.entity;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@DataObject(generateConverter = true)
public class Todo {
    private static final AtomicInteger acc = new AtomicInteger(0);

    private int id;
    private String title;
    private Boolean completed;
    private Integer order;
    private String url;

    public Todo() {}

    public Todo(Todo other) {
        this.id = other.id;
        this.title = other.title;
        this.completed = other.completed;
        this.order = other.order;
        this.url = other.url;
    }

    public Todo(JsonObject obj) {
        TodoConverter.fromJson(obj, this);
    }

    public Todo(String jsonStr) {
        TodoConverter.fromJson(new JsonObject(jsonStr), this);
    }

    public Todo(int id, String title, Boolean completed, Integer order, String url) {
        this.id = id;
        this.title = title;
        this.completed = completed;
        this.order = order;
        this.url = url;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        TodoConverter.toJson(this, json);
        return json;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setIncId() {
        this.id = acc.incrementAndGet();
    }

    public static int getIncId() {
        return acc.get();
    }

    public static void setIncIdWith(int n) {
        acc.set(n);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getCompleted() {
        return Optional.ofNullable(completed).orElse(false);
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public Integer getOrder() {
        return Optional.ofNullable(order).orElse(0);
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Todo todo = (Todo) o;
        return id == todo.id &&
                title.equals(todo.title) &&
                Objects.equals(completed, todo.completed) &&
                Objects.equals(order, todo.order);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, completed, order);
    }

    @Override
    public String toString() {
        return "Todo{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", completed=" + completed +
                ", order=" + order +
                ", url='" + url + '\'' +
                '}';
    }

    public Todo merge(Todo todo) {
        return new Todo(id,
                Optional.ofNullable(todo.title).orElse(title),
                Optional.ofNullable(todo.completed).orElse(completed),
                Optional.ofNullable(todo.order).orElse(order),
                url);
    }
}
