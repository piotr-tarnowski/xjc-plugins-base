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
            generate
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
                    JDefinedClass implClass = classOutline.implClass;
                    JDefinedClass newClass;
                    String newClassName = implClass.name() + "Filter";
                    if (pckg == CURRENT_PACKAGE) {
                        JClassContainer parent = implClass.parentContainer();
                        newClass = parent._class(JMod.PUBLIC + JMod.ABSTRACT, newClassName, ClassType.CLASS);
                    } else {
                        newClass = codeModel._class(JMod.PUBLIC + JMod.ABSTRACT, pckg + "." + newClassName, ClassType.CLASS);
                    }
                    JClass adapter = codeModel.ref(XmlAdapter.class).narrow(implClass, implClass);
                    newClass._extends(adapter);
                    JFieldVar counter = newClass.field(JMod.PROTECTED, int.class, "counter");
                    createIdentityMethod("unmarshal", newClass, implClass);

                    JMethod valid = newClass.method(JMod.PUBLIC, boolean.class, "valid");
                    valid.body()._return(counter.gt(JExpr.lit(0)));

                    JMethod test = newClass.method(JMod.PUBLIC, boolean.class, "test");
                    test.param(implClass, "entity");
                    test.body()._return(JExpr.TRUE);

                    JMethod marshal = newClass.method(JMod.PUBLIC, implClass, "marshal");
                    JVar param = marshal.param(implClass, "entity");
                    final JConditional condition = marshal.body()._if(JExpr.invoke(test).arg(param));
                    condition._then()._return(JExpr._null());
                    final JBlock block = condition._else();
                    block.directStatement("counter++;");
                    block._return(param);
                    marshal.annotate(Override.class);
                } catch (JClassAlreadyExistsException ex) {
                    ex.printStackTrace();
                }
            }
        });
        handleDeclaredCustomizations(omodel, opt, errorHandler);
        return true;
    }

    private void createIdentityMethod(String name, JDefinedClass newClass, JDefinedClass implClass) {
        JMethod method = newClass.method(JMod.PUBLIC, implClass, name);
        JVar param = method.param(implClass, "entity");
        method.body()._return(param);
        method.annotate(Override.class);
    }
}
