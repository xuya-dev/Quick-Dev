package dev.xuya.core.translate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.PropertyMetadata;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.VirtualBeanPropertyWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link TranslateMode#APPEND 附加模式} 的 Jackson 模块：注册为 Spring Bean 后
 * 由 Spring Boot 自动装配进 ObjectMapper，序列化期为每个 APPEND 字段追加一个
 * 兄弟属性输出翻译结果（字段保留原值，由 {@link TranslateSerializer} 透传）。
 *
 * <p>REPLACE 模式不经本模块（仍是注解序列化器直接替换，保持 0.1.x 行为）。</p>
 */
public class TranslateAppendModule extends Module {

    private static final Logger log = LoggerFactory.getLogger(TranslateAppendModule.class);

    private final Modifier modifier = new Modifier();

    @Override
    public String getModuleName() {
        return "quick-dev-translate-append";
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addBeanSerializerModifier(modifier);
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    private static class Modifier extends BeanSerializerModifier {

        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                         BeanDescription beanDesc,
                                                         List<BeanPropertyWriter> properties) {
            List<BeanPropertyWriter> changed = null;
            for (BeanPropertyWriter writer : properties) {
                Translate anno = writer.getAnnotation(Translate.class);
                if (anno == null || anno.mode() != TranslateMode.APPEND
                        || (anno.dict().isEmpty() && anno.enumClass() == Void.class
                        && (anno.entity() == Void.class || anno.field().isEmpty()))) {
                    continue;
                }
                String appendName = anno.appendField().isEmpty()
                        ? writer.getName() + "Name" : anno.appendField();
                if (hasProperty(properties, appendName)) {
                    log.debug("附加翻译字段 [{}] 与实体 {} 既有属性重名，跳过",
                            appendName, beanDesc.getBeanClass().getSimpleName());
                    continue;
                }
                if (changed == null) {
                    changed = new ArrayList<>(properties);
                }
                changed.add(new TranslateVirtualPropertyWriter(appendName, anno, writer,
                        config.constructType(String.class)));
            }
            return changed != null ? changed : properties;
        }

        private boolean hasProperty(List<BeanPropertyWriter> properties, String name) {
            for (BeanPropertyWriter writer : properties) {
                if (writer.getName().equals(name)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 虚拟属性：输出值为对源字段执行翻译的结果；无翻译结果时不输出该属性
     * （BeanPropertyWriter 对 null 值且无 nullSerializer 的字段默认跳过）。
     */
    private static class TranslateVirtualPropertyWriter extends VirtualBeanPropertyWriter {

        private final Translate annotation;
        private final BeanPropertyWriter source;

        private TranslateVirtualPropertyWriter(String name, Translate annotation,
                                               BeanPropertyWriter source, JavaType type) {
            // member 借用源字段的（Jackson 构造期 fixAccess 需要），声明类型固定为 String
            super(new ConstantNameDefinition(name, source.getMember(), type), null, type);
            this.annotation = annotation;
            this.source = source;
        }

        @Override
        protected Object value(Object bean, JsonGenerator gen,
                               SerializerProvider prov) throws Exception {
            TranslateExecutor executor = TranslateExecutor.getInstance();
            if (executor == null) {
                return null;
            }
            return executor.translate(annotation, source.get(bean));
        }

        @Override
        public VirtualBeanPropertyWriter withConfig(MapperConfig<?> config, AnnotatedClass declaringClass,
                                                    BeanPropertyDefinition propDef, JavaType type) {
            // 构造时已持有全部配置（注解/源字段），无需按 Bean 重构
            return this;
        }
    }

    /**
     * 仅携带名称与声明类型的 BeanPropertyDefinition（虚拟属性借用源字段的 member）
     */
    private static class ConstantNameDefinition extends BeanPropertyDefinition {

        private final String name;
        private final AnnotatedMember member;
        private final JavaType type;

        private ConstantNameDefinition(String name, AnnotatedMember member, JavaType type) {
            this.name = name;
            this.member = member;
            this.type = type;
        }

        @Override
        public BeanPropertyDefinition withName(PropertyName newName) {
            return this;
        }

        @Override
        public BeanPropertyDefinition withSimpleName(String newSimpleName) {
            return this;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public PropertyName getFullName() {
            return new PropertyName(name);
        }

        @Override
        public String getInternalName() {
            return name;
        }

        @Override
        public PropertyName getWrapperName() {
            return null;
        }

        @Override
        public boolean isExplicitlyIncluded() {
            return false;
        }

        @Override
        public JavaType getPrimaryType() {
            return type;
        }

        @Override
        public Class<?> getRawPrimaryType() {
            return type.getRawClass();
        }

        @Override
        public PropertyMetadata getMetadata() {
            return PropertyMetadata.STD_OPTIONAL;
        }

        @Override
        public boolean hasGetter() {
            return false;
        }

        @Override
        public boolean hasSetter() {
            return false;
        }

        @Override
        public boolean hasField() {
            return false;
        }

        @Override
        public boolean hasConstructorParameter() {
            return false;
        }

        @Override
        public AnnotatedMethod getGetter() {
            return null;
        }

        @Override
        public AnnotatedMethod getSetter() {
            return null;
        }

        @Override
        public AnnotatedField getField() {
            return null;
        }

        @Override
        public AnnotatedParameter getConstructorParameter() {
            return null;
        }

        @Override
        public AnnotatedMember getPrimaryMember() {
            return member;
        }

        @Override
        public JsonInclude.Value findInclusion() {
            return null;
        }
    }
}
