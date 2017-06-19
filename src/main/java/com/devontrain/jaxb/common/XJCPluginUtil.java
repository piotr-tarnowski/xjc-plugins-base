package com.devontrain.jaxb.common;

import com.sun.codemodel.*;
import com.sun.tools.xjc.model.CPluginCustomization;
import com.sun.tools.xjc.outline.ClassOutline;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class XJCPluginUtil {
    private XJCPluginUtil() {

    }

    private final static Map<Class<?>, Class<?>> primitives = new HashMap<>();

    static {
        primitives.put(boolean.class, Boolean.class);
        primitives.put(byte.class, Byte.class);
        primitives.put(short.class, Short.class);
        primitives.put(char.class, Character.class);
        primitives.put(int.class, Integer.class);
        primitives.put(long.class, Long.class);
        primitives.put(float.class, Float.class);
        primitives.put(double.class, Double.class);
        primitives.put(Boolean.class, Boolean.class);
        primitives.put(Byte.class, Byte.class);
        primitives.put(Short.class, Short.class);
        primitives.put(Character.class, Character.class);
        primitives.put(Integer.class, Integer.class);
        primitives.put(Long.class, Long.class);
        primitives.put(Float.class, Float.class);
        primitives.put(Double.class, Double.class);
        primitives.put(String.class, String.class);
    }

    public static void replaceGetter(String fieldName, ClassOutline co, JFieldVar f, JType setImplType) {
        //Create the method name
        String methodName = resolveMethodName("get", fieldName);


        //Find and remove Old Getter!
        JMethod oldGetter = co.implClass.getMethod(methodName, new JType[0]);
        co.implClass.methods().remove(oldGetter);

        //Create New Getter
        JMethod getter = co.implClass.method(JMod.PUBLIC, f.type(), resolveMethodName("get", f.name()));

        //Create Getter Body -> {if (f = null) f = new HashSet(); return f;}
        getter.body()._if(JExpr.ref(f.name()).eq(JExpr._null()))._then()
                .assign(f, JExpr._new(setImplType));

        getter.body()._return(JExpr.ref(f.name()));
    }

    public static void createSetter(String fieldName, ClassOutline co, JFieldVar f, JType setType, boolean force) {
        //Create the method name
        String methodName = resolveMethodName("set", fieldName);

        //Find and remove Old Setter if exists!
        Iterator<JMethod> iterator = co.implClass.methods().stream().filter(m -> m.name().equals(methodName)).iterator();
        if (iterator.hasNext()) {
            JMethod oldSetter = iterator.next();
            co.implClass.methods().remove(oldSetter);
            force = true;
        }

        if (force) {
            JType voidType = co.parent().getCodeModel().VOID;
            JMethod setter = co.implClass.method(JMod.PUBLIC, voidType, resolveMethodName("set", f.name()));

            //Create HashSet JType
            JVar param = setter.param(setType, f.name());
            setter.body().assign(JExpr.refthis(f.name()), param);
        }
    }

    public static String nameFromSetter(String setter) {
        char[] chars = setter.toCharArray();
        chars[3] = Character.toLowerCase(chars[3]);
        return new String(chars, 3, chars.length - 3);
    }

    public static String setterForName(String name) {
        return resolveMethodName("set", name);
    }


    public static String resolveMethodName(String prefix, String fieldName) {
        final StringBuilder sb = new StringBuilder(fieldName.length() + 3);
        sb.append(prefix);
        sb.append(fieldName);
        sb.setCharAt(3, Character.toUpperCase(fieldName.charAt(0)));
        return sb.toString();
    }

    public static JType resolveType(CPluginCustomization cp, ClassOutline co, JType inner, String typeAttr, String defaultTypeName, ErrorHandler errorHandler) throws SAXException {
        String typeName = cp.element.getAttribute(typeAttr);
        if (typeName.length() == 0) {
            typeName = defaultTypeName;
        }
        Class<?> setImpl = null;
        try {
            System.err.println("typeName:[" + typeName + "]");
            setImpl = Class.forName(typeName);
        } catch (ClassNotFoundException e) {
            errorHandler.error(new SAXParseException(e.getMessage(), null, e));
        }
        return co.parent().getCodeModel().ref(setImpl).narrow(inner);
    }

    @SuppressWarnings("unchecked")
    public static Object convert(String value, Class<?> type) {
        Object tmp = null;
        if (String.class == type) {
            tmp = value;
        } else if (type.isEnum()) {
            tmp = Enum.valueOf((Class<Enum>) type, value);
        } else {
            Class<?> ptype = primitives.get(type);
            if (ptype != null) {
                try {
                    Constructor<?> constructor = ptype.getConstructor(String.class);
                    tmp = constructor.newInstance(value);
                } catch (Exception e) {
                    System.err.println(ptype + " " + value);
                    e.printStackTrace();
                }
            }
        }
        return tmp;
    }

    static void describeArgument(StringBuilder sb, String operation, String name, final Class<?> type, final XJCPluginProperty annotation) {
        sb.append(operation);
        sb.append(":");
        sb.append(name);
        sb.append("[=");
        sb.append(type.getSimpleName().toLowerCase());
        sb.append("(default");
        sb.append(annotation.defaultValue());
        sb.append(")]");
        sb.append(" ");
    }

}
