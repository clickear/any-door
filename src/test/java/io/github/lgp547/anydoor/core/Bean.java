package io.github.lgp547.anydoor.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lgp547.anydoor.dto.User;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Objects;

@Component
public class Bean {

    public static final Integer id = 1;

    public static final String name = "AnyDoorUserName";

    public static final User user = new User(1, "AnyDoorUserName1");

    public static final List<String> strings = List.of("AnyDoorStr1", "AnyDoorStr2");

    public static final List<User> users = List.of(new User(2, "2"), new User(3, "3"));


    public static JsonNode getContent() {
        ObjectNode jsonNode = new ObjectNode(JsonNodeFactory.instance);
        jsonNode.put("id", id);
        jsonNode.put("name", name);
        jsonNode.putPOJO("user", user);
        jsonNode.putPOJO("strings", strings);
        jsonNode.putPOJO("users", users);
        return jsonNode;
    }

    private void noParamPrivate() {
        System.out.println("noParamPrivate");
    }

    public void noParam() {
        System.out.println("noParam");
    }

    public String oneParam(String name) {
        Assert.isTrue(Bean.name.equals(name));
        return name;
    }

    public Long oneParam(Long nullParam) {
        Assert.isNull(nullParam);
        return nullParam;
    }

    public User oneParam(User user) {
        Assert.isTrue(Bean.user.equals(user));
        return user;
    }

    public List<String> oneParam1(List<String> strings) {
        Assert.isTrue(Bean.strings.equals(strings));
        return strings;
    }

    public List<User> oneParam2(List<User> users) {
        Objects.requireNonNull(users);
        for (int i = 0; i < Bean.users.size(); i++) {
            Assert.isTrue(Bean.users.get(i).equals(users.get(i)));
        }
        return users;
    }

    public JsonNode multipleParam(String name, Integer id) {
        Assert.isTrue(Bean.name.equals(name));
        Assert.isTrue(Bean.id.equals(id));
        ObjectNode jsonNode = new ObjectNode(JsonNodeFactory.instance);
        jsonNode.put("name", name);
        jsonNode.put("id", id);
        return jsonNode;
    }

    public JsonNode multipleParam(Integer id, String name) {
        Assert.isTrue(Bean.name.equals(name));
        Assert.isTrue(Bean.id.equals(id));
        ObjectNode jsonNode = new ObjectNode(JsonNodeFactory.instance);
        jsonNode.put("name", name);
        jsonNode.put("id", id);
        return jsonNode;
    }
}