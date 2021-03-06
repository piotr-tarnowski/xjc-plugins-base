package com.devontrain.jaxb.plugins;

import com.devontrain.jaxb.common.*;
import com.sun.codemodel.*;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.model.CPluginCustomization;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Created by @author <a href="mailto:piotr.tarnowski.dev@gmail.com">Piotr Tarnowski</a> on 23.06.17.
 */
@XJCPlugin(name = "Xfilter")
public class JAXBFilterXJCPlugin extends XJCPluginBase {

    private static final String CURRENT_PACKAGE = "*";

    @XJCPluginProperty(defaultValue = CURRENT_PACKAGE)
    private String pckg = CURRENT_PACKAGE;

    @XJCPluginCustomizations(uri = "http://common.jaxb.devontrain.com/plugin/filter-generator")
    private interface filter {
        enum cust {
            @AllowedAttributes({attrs.class})
            generate;

            public interface attrs {

                String base = "base";
                String param = "param";
            }
        }
    }

    @Override
    public boolean run(Outline omodel, Options opt, ErrorHandler errorHandler) throws SAXException {

        JCodeModel codeModel = omodel.getCodeModel();
        getClassCustomizationHandlers().add(new ClassCustomizationHandler<filter.cust>() {
            @Override
            @SuppressWarnings("StringEquality")
            public void handle(ClassOutline classOutline, filter.cust cust, CPluginCustomization cp) throws SAXException {
                try {

                    JClass iface;
                    JDefinedClass implClass = classOutline.implClass;
                    JDefinedClass newClass;
                    String newClassName = implClass.name() + "Filter";
                    if (pckg == CURRENT_PACKAGE) {
                        JClassContainer parent = implClass.parentContainer();
                        try {
                            iface = parent._class(JMod.PUBLIC, "Filter", ClassType.INTERFACE);
                            declareFilterInterface((JDefinedClass) iface);
                        } catch (JClassAlreadyExistsException e) {
                            iface = codeModel.ref(parent.getPackage().name() + ".Filter");
                        }
                        int mods = JMod.PUBLIC;
                        if (parent.isClass()) {
                            mods += JMod.STATIC;
                        }
                        newClass = parent._class(mods, newClassName, ClassType.CLASS);
                    } else {
                        newClass = codeModel._class(JMod.PUBLIC, pckg + "." + newClassName, ClassType.CLASS);
                        try {
                            iface = codeModel._class(JMod.PUBLIC, pckg + ".Filter", ClassType.INTERFACE);
                            declareFilterInterface((JDefinedClass) iface);
                        } catch (JClassAlreadyExistsException e) {
                            iface = codeModel.ref(pckg + ".Filter");
                        }
                    }
                    newClass._implements(iface);

                    JMethod constructor = newClass.constructor(JMod.PUBLIC);
                    constructor.body().directStatement("super();");
                    String baseClass = cp.element.getAttribute(filter.cust.attrs.base);
                    JClass adapter;
                    if ("".equals(baseClass)) {
                        adapter = codeModel.ref(XmlAdapter.class).narrow(implClass, implClass);
                    } else {
                        adapter = codeModel.ref(baseClass);
                        String qualifiedClassName = cp.element.getAttribute(filter.cust.attrs.param);
                        if (!"".equals(qualifiedClassName)) {
                            JMethod constr = newClass.constructor(JMod.PUBLIC);
                            constr.param(codeModel.ref(qualifiedClassName), "param");
                            constr.body().directStatement("super(param);");
                        }
                    }
                    newClass._extends(adapter);
                    implClass.annotate(XmlJavaTypeAdapter.class).param("value", newClass);
                    JMethod method = newClass.method(JMod.PUBLIC, implClass, "unmarshal");
                    method.annotate(Override.class);
                    JVar entity = method.param(implClass, "entity");
                    if ("".equals(baseClass)) {
                        method.body()._return(entity);
                    } else {
                        method.body()._return(JExpr.invoke(JExpr._super(), "unmarshal").arg(entity));
                    }

                    JFieldVar inactive = newClass.field(JMod.PROTECTED, boolean.class, "inactive", JExpr.lit(true));
                    JFieldVar counter = newClass.field(JMod.PROTECTED, int.class, "counter");
                    JMethod valid = newClass.method(JMod.PUBLIC, boolean.class, "valid");
                    valid.annotate(Override.class);
                    valid.body()._return(inactive.cor(counter.gt(JExpr.lit(0))));

                    JMethod accept = newClass.method(JMod.PUBLIC, boolean.class, "accept");
                    accept.param(implClass, "entity");
                    accept.body()._return(JExpr.FALSE);

                    JMethod reset = newClass.method(JMod.PUBLIC, void.class, "reset");
                    reset.annotate(Override.class);
                    final JBlock jBlock = reset.body();
                    jBlock.assign(counter,JExpr.lit(0));
                    jBlock.assign(inactive,JExpr.lit(true));

                    JMethod marshal = newClass.method(JMod.PUBLIC, implClass, "marshal");
                    JVar param = marshal.param(implClass, "entity");
                    JBlock body = marshal.body();
                    body._if(inactive)._then().assign(inactive, JExpr.lit(false));
                    JConditional condition = body._if(JExpr.invoke(accept).arg(param));
                    condition._then()._return(JExpr._null());
                    JBlock block = condition._else();
                    block.directStatement("counter++;");
                    if ("".equals(baseClass)) {
                        block._return(param);
                    } else {
                        block._return(JExpr.invoke(JExpr._super(), "marshal").arg(entity));
                    }
                    marshal.annotate(Override.class);
                } catch (JClassAlreadyExistsException ex) {
                    ex.printStackTrace();
                }
            }
        });
        handleDeclaredCustomizations(omodel, opt, errorHandler);
        return true;
    }

    private void declareFilterInterface(JDefinedClass iface) {
        iface.method(JMod.PUBLIC, boolean.class, "valid");
        iface.method(JMod.PUBLIC, void.class, "reset");
    }
}
