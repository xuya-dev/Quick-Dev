package dev.xuya.core.translate;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/**
 * @Translate 的 Jackson 序列化器：序列化时把字段值替换为翻译结果（失败保留原值）。
 * 通过 ContextualSerializer 机制拿到字段上的注解实例。
 */
public class TranslateSerializer extends StdSerializer<Object> implements ContextualSerializer {

    private transient Translate annotation;

    public TranslateSerializer() {
        super(Object.class);
    }

    private TranslateSerializer(Translate annotation) {
        super(Object.class);
        this.annotation = annotation;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        TranslateExecutor executor = TranslateExecutor.getInstance();
        String translated = executor == null ? null : executor.translate(annotation, value);
        if (translated != null) {
            gen.writeString(translated);
        } else {
            gen.writeObject(value);
        }
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property) {
        if (property != null) {
            Translate anno = property.getAnnotation(Translate.class);
            if (anno != null && (!anno.dict().isEmpty() || anno.enumClass() != Void.class
                    || (anno.entity() != Void.class && !anno.field().isEmpty()))) {
                return new TranslateSerializer(anno);
            }
        }
        return this;
    }
}
