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

import java.util.*;

import static com.devontrain.jaxb.common.CodeModelUtil.*;

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

            public interface mod_attrs {
                String iface = "iface";
                String impl = "impl";
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
                    String fieldName = field.getPropertyInfo().getName(false);
                    String newName = cp.element.getAttribute(coll.cust.mod_attrs.name);
                    if ("".equals(newName)) {
                        newName = fieldName;
                    }
                    ClassOutline co = field.parent();
                    JDefinedClass implClass = co.implClass;
                    Map<String, JFieldVar> fields = implClass.fields();
                    JFieldVar var = fields.get(fieldName);
                    changeXMLTypePropOrderAnnotataion(co, fieldName, newName);
                    changeXMLElementNameAnnotation(var, newName);
                    switch (cust) {
                        case mod:
                            JClass inner = ((JClass) (field.getRawType())).getTypeParameters().get(0);
                            JClass setType = resolveType(cp, co, inner, coll.cust.mod_attrs.iface, Set.class.getCanonicalName(), errorHandler);
                            var.type(setType);
                            if (newName.length() > 0) {
                                var.name(newName);
                            }
                            JClass returnType = resolveType(cp, co, inner, coll.cust.mod_attrs.impl, HashSet.class.getCanonicalName(), errorHandler);
                            replaceGetter(fieldName, co, var, returnType);
                            String setter = cp.element.getAttribute(coll.cust.mod_attrs.setter);
                            JClass assignType = resolveType(cp, co, inner.wildcard(), coll.cust.mod_attrs.iface, HashSet.class.getCanonicalName(), errorHandler);
                            createSetter(fieldName, co, var, setType, assignType, Boolean.TRUE.toString().equalsIgnoreCase(setter));
                            break;
                        default:
                    }
                }
            }

        });
        handleDeclaredCustomizations(outline, opt, errorHandler);
        return true;
    }
}