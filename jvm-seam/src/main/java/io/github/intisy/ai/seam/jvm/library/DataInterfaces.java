package io.github.intisy.ai.seam.jvm.library;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads JSON into the contract's data types, which are interfaces rather than classes.
 *
 * @implNote {@code @TsInterface(data = true)} and {@code @TsOptional} are SOURCE-retained, so
 * neither which interfaces are data nor which properties are optional is visible at run time. Any
 * interface whose methods are all abstract and take no arguments is therefore read as data, and an
 * absent property answers null for a reference type and is refused for a primitive one; which
 * properties are required is held by the declaration's result schema. {@code @TsLiteral} is
 * SOURCE-retained too, so an enum constant is read from its own name or from that name in lower
 * case with each {@code _} written {@code -}, the form every contract literal takes.
 */
final class DataInterfaces {

    private DataInterfaces() {
    }

    /** @return a factory reading every data interface and every enum, and nothing else */
    static TypeAdapterFactory typeAdapterFactory() {
        return new TypeAdapterFactory() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                Class<?> raw = type.getRawType();
                if (raw.isEnum()) {
                    return (TypeAdapter<T>) new EnumAdapter(raw);
                }
                return isDataInterface(raw) ? (TypeAdapter<T>) new DataInterfaceAdapter(gson, raw) : null;
            }
        };
    }

    static boolean isDataInterface(Class<?> candidate) {
        if (!candidate.isInterface() || candidate.isAnnotation()) {
            return false;
        }
        List<Method> properties = propertiesOf(candidate);
        if (properties.isEmpty()) {
            return false;
        }
        for (Method property : properties) {
            if (property.isDefault() || property.getParameterTypes().length != 0 || property.getReturnType() == void.class) {
                return false;
            }
        }
        return true;
    }

    private static List<Method> propertiesOf(Class<?> dataInterface) {
        List<Method> properties = new ArrayList<Method>();
        for (Method method : dataInterface.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) && !isObjectMethod(method)) {
                properties.add(method);
            }
        }
        return properties;
    }

    private static boolean isObjectMethod(Method method) {
        int parameters = method.getParameterTypes().length;
        String name = method.getName();
        return parameters == 0 && (name.equals("hashCode") || name.equals("toString"))
                || parameters == 1 && name.equals("equals") && method.getParameterTypes()[0] == Object.class;
    }

    private static final class DataInterfaceAdapter extends TypeAdapter<Object> {
        private final Gson gson;
        private final Class<?> dataInterface;

        DataInterfaceAdapter(Gson gson, Class<?> dataInterface) {
            this.gson = gson;
            this.dataInterface = dataInterface;
        }

        @Override
        public Object read(JsonReader reader) throws IOException {
            JsonElement element = gson.getAdapter(JsonElement.class).read(reader);
            if (element.isJsonNull()) {
                return null;
            }
            if (!element.isJsonObject()) {
                throw new JsonParseException(dataInterface.getSimpleName() + " is read from a JSON object, and the answer holds " + element);
            }
            JsonObject object = element.getAsJsonObject();
            return Proxy.newProxyInstance(dataInterface.getClassLoader(), new Class<?>[] {dataInterface},
                    new DataHandler(dataInterface, object, valuesOf(object)));
        }

        private Map<String, Object> valuesOf(JsonObject object) {
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            for (Method property : propertiesOf(dataInterface)) {
                values.put(property.getName(), valueOf(property, object.get(property.getName())));
            }
            return values;
        }

        private Object valueOf(Method property, JsonElement value) {
            Object converted = value == null ? null : gson.getAdapter(TypeToken.get(property.getGenericReturnType())).fromJsonTree(value);
            if (converted == null && property.getReturnType().isPrimitive()) {
                throw new JsonParseException(dataInterface.getSimpleName() + "." + property.getName() + " is "
                        + (value == null ? "absent" : "null") + ", and a " + property.getReturnType() + " cannot be");
            }
            return converted;
        }

        @Override
        public void write(JsonWriter writer, Object value) {
            throw new UnsupportedOperationException(dataInterface.getName() + " is read from JSON and never written to it");
        }
    }

    /** Answers each property from the values read when the proxy was built, and compares by the JSON it was read from. */
    private static final class DataHandler implements InvocationHandler {
        private final Class<?> dataInterface;
        private final JsonObject json;
        private final Map<String, Object> values;

        DataHandler(Class<?> dataInterface, JsonObject json, Map<String, Object> values) {
            this.dataInterface = dataInterface;
            this.json = json;
            this.values = values;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (!isObjectMethod(method)) {
                return values.get(method.getName());
            }
            if (method.getName().equals("equals")) {
                return isEqualData(arguments[0]);
            }
            if (method.getName().equals("hashCode")) {
                return json.hashCode();
            }
            return dataInterface.getSimpleName() + json;
        }

        private boolean isEqualData(Object other) {
            if (other == null || !Proxy.isProxyClass(other.getClass())) {
                return false;
            }
            InvocationHandler handler = Proxy.getInvocationHandler(other);
            return handler instanceof DataHandler
                    && ((DataHandler) handler).dataInterface == dataInterface
                    && ((DataHandler) handler).json.equals(json);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class EnumAdapter extends TypeAdapter<Object> {
        private final Class<?> enumType;

        EnumAdapter(Class<?> enumType) {
            this.enumType = enumType;
        }

        @Override
        public Object read(JsonReader reader) throws IOException {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull();
                return null;
            }
            String literal = reader.nextString();
            for (Object constant : enumType.getEnumConstants()) {
                String name = ((Enum) constant).name();
                if (name.equals(literal) || literalOf(name).equals(literal)) {
                    return constant;
                }
            }
            throw new JsonParseException("\"" + literal + "\" names no constant of " + enumType.getSimpleName());
        }

        private static String literalOf(String constantName) {
            return constantName.toLowerCase(Locale.ROOT).replace('_', '-');
        }

        @Override
        public void write(JsonWriter writer, Object value) throws IOException {
            if (value == null) {
                writer.nullValue();
            } else {
                writer.value(literalOf(((Enum) value).name()));
            }
        }
    }
}
