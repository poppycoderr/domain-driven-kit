package com.ddk.core.jackson;

import com.ddk.core.domain.Identifier;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.Deserializers;
import tools.jackson.databind.module.SimpleModule;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * 让类型化标识以原始值出现在 JSON 里：{@code UserId.of(42L)} 写成 {@code 42}，读回来仍是 {@code UserId}。
 * <p>
 * {@link Identifier} 的值是私有字段、访问方法叫 {@code value()}，Jackson 默认既看不到也构造不了，
 * 会把它写成 {@code {}} 并在读取时失败。领域层不能为此加 Jackson 注解，所以由这个模块统一处理：
 * 读取时优先调用子类的静态工厂 {@code of(T)}，没有时退回单参数构造器，两者都会执行子类自己的取值校验。
 */
public class IdentifierJacksonModule extends SimpleModule {

    public IdentifierJacksonModule() {
        super("ddk-identifier");
        addSerializer(Identifier.class, new IdentifierSerializer());
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addDeserializers(new IdentifierDeserializers());
    }

    @SuppressWarnings("rawtypes")
    private static final class IdentifierSerializer extends ValueSerializer<Identifier> {

        @Override
        public void serialize(Identifier value, JsonGenerator generator, SerializationContext context) throws JacksonException {
            context.writeValue(generator, value.value());
        }
    }

    private static final class IdentifierDeserializers extends Deserializers.Base {

        @Override
        public @Nullable ValueDeserializer<?> findBeanDeserializer(JavaType type, DeserializationConfig config, BeanDescription.Supplier beanDesc) {
            Class<?> raw = type.getRawClass();
            if (!Identifier.class.isAssignableFrom(raw) || Modifier.isAbstract(raw.getModifiers())) {
                return null;
            }
            return new IdentifierDeserializer(creator(raw));
        }

        @Override
        public boolean hasDeserializerFor(DeserializationConfig config, Class<?> valueType) {
            return Identifier.class.isAssignableFrom(valueType) && !Modifier.isAbstract(valueType.getModifiers());
        }

        private static Executable creator(Class<?> type) {
            return Arrays.stream(type.getDeclaredMethods())
                    .filter(m -> Modifier.isStatic(m.getModifiers()) && m.getName().equals("of") && m.getParameterCount() == 1
                            && type.isAssignableFrom(m.getReturnType()))
                    .<Executable>map(m -> m)
                    .findFirst()
                    .or(() -> Arrays.stream(type.getDeclaredConstructors()).filter(c -> c.getParameterCount() == 1).<Executable>map(c -> c).findFirst())
                    .orElseThrow(() -> new IllegalStateException(type.getName() + " needs a static of(value) factory or a single-argument constructor"));
        }
    }

    private static final class IdentifierDeserializer extends ValueDeserializer<Identifier<?>> {

        private final Executable creator;

        IdentifierDeserializer(Executable creator) {
            this.creator = creator;
            creator.setAccessible(true);
        }

        @Override
        public Identifier<?> deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            Object raw = context.readValue(parser, creator.getParameterTypes()[0]);
            try {
                Object id = creator instanceof Method method ? method.invoke(null, raw) : ((Constructor<?>) creator).newInstance(raw);
                return (Identifier<?>) id;
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                return context.reportInputMismatch(this, "Invalid %s: %s", creator.getDeclaringClass().getSimpleName(), cause.getMessage());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot create " + creator.getDeclaringClass().getName(), e);
            }
        }
    }
}
