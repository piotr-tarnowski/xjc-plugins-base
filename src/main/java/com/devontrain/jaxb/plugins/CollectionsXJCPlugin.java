package com.devontrain.jaxb.plugins;

import com.devontrain.jaxb.common.*;
import com.sun.codemodel.*;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.generator.bean.field.UntypedListField;
import com.sun.tools.xjc.model.CPluginCustomization;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Created by @author <a href="mailto:piotr.tarnowski.dev@gmail.com">Piotr Tarnowski</a> on 11.01.17.
 */
@XJCPlugin(name = "Xcoll")
public class CollectionsXJCPlugin extends XJCPluginBase {

    @XJCPluginCustomizations(uri = "http://common.jaxb.devontrain.com/plugin/collection-manipulator")
    private interface coll {
        enum cust {

            @AllowedAttributes({mod_attrs.class})
            mod;

            @Required
            public interface mod_attrs {
                String set = "set";
                String setter = "setter";
                String name = "name";
            }

        }
    }

    @Override
    public boolean run(Outline outline, Options opt, ErrorHandler errorHandler) throws SAXException {
        getFieldCustomizationHandlers().add(new FieldCustomizationHandler<coll.cust>() {

            @Override
            public void handle(FieldOutline outline, coll.cust cust, CPluginCustomization cp) throws SAXException {
                if (outline instanceof UntypedListField) {
                    UntypedListField field = (UntypedListField) outline;
                    ClassOutline co = field.parent();
                    JDefinedClass implClass = co.implClass;
                    Map<String, JFieldVar> fields = implClass.fields();
                    String fieldName = field.getPropertyInfo().getName(false);
                    JFieldVar var = fields.get(fieldName);
                    switch (cust) {
                        case mod:
                            JType inner = ((JClass) (field.getRawType())).getTypeParameters().get(0);
                            JType setType = co.parent().getCodeModel().ref(Set.class).narrow(inner);
                            var.type(setType);
                            String newName = cp.element.getAttribute(coll.cust.mod_attrs.name);
                            if (newName != null) {
                                var.name(newName);
                            }

                            String set = cp.element.getAttribute(coll.cust.mod_attrs.set);
                            Class<?> setImpl = null;
                            try {
                                setImpl = Class.forName(set);
                            } catch (ClassNotFoundException e) {
                                errorHandler.error(new SAXParseException(null, null, e));
                            }
                            JType setImplType = co.parent().getCodeModel().ref(setImpl).narrow(inner);

                            replaceGetter(fieldName, co, var, setImplType);

                            String setter = cp.element.getAttribute(coll.cust.mod_attrs.setter);
                            createSetter(fieldName, co, var, setType, Boolean.TRUE.toString().equalsIgnoreCase(setter));
                            break;
                        default:
                    }
                }
            }

        });

        handleDeclaredCustomizations(outline, opt, errorHandler);

        return true;
    }


    private void replaceGetter(String fieldName, ClassOutline co, JFieldVar f, JType setImplType) {
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

    private void createSetter(String fieldName, ClassOutline co, JFieldVar f, JType setType, boolean force) {
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

    private String resolveMethodName(String prefix, String fieldName) {
        return prefix + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }
}