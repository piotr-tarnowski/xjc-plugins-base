package com.devontrain.jaxb.plugins;

import com.devontrain.jaxb.common.XJCPlugin;
import com.devontrain.jaxb.common.XJCPluginBase;
import com.devontrain.jaxb.common.XJCPluginCustomizations;
import com.devontrain.jaxb.common.XJCPluginProperty;
import com.sun.codemodel.*;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.util.Iterator;

/**
 * Created by @author <a href="mailto:piotr.tarnowski.dev@gmail.com">Piotr Tarnowski</a> on 23.06.17.
 */
@XJCPlugin(name = "Xfilter")
public class JAXBFilterXJCPlugin extends XJCPluginBase {

    private static final String CURRENT_PACKAGE = "*";

    @XJCPluginProperty(defaultValue = CURRENT_PACKAGE)
    private String pckg = CURRENT_PACKAGE;

    @XJCPluginCustomizations(uri = "http://common.jaxb.devontrain.com/plugin/filter-generator")
    private interface coll {
    }

    @Override
    public boolean run(Outline omodel, Options opt, ErrorHandler errorHandler) throws SAXException {


        JCodeModel codeModel = omodel.getCodeModel();


        Iterator<? extends ClassOutline> iterator = omodel.getClasses().iterator();
        while (iterator.hasNext()) {
            ClassOutline classOutline = iterator.next();

            try {
                JDefinedClass implClass = classOutline.implClass;
                JDefinedClass newClass;
                String newClassName = implClass.name() + "Filter";
                if (pckg == CURRENT_PACKAGE) {
                    JClassContainer parent = implClass.parentContainer();
                    newClass = parent._class(newClassName);
                } else {
                    newClass = codeModel._class(pckg + "." + newClassName);
                }
                JClass adapter = codeModel.ref(XmlAdapter.class).narrow(implClass, implClass);
                //TODO: inherit class from parameter
                newClass._extends(adapter);
                createIdentityMethod("unmarshal", newClass, implClass);
                //TODO: rather then identity we shall invoke filter method
                createIdentityMethod("marshal", newClass, implClass);
            } catch (JClassAlreadyExistsException ex) {
                ex.printStackTrace();
            }

        }
        return true;
    }

    private void createIdentityMethod(String name, JDefinedClass newClass, JDefinedClass implClass) {
        JMethod method = newClass.method(JMod.PUBLIC, implClass, name);
        JVar param = method.param(implClass, "v");
        method.body()._return(param);
        method.annotate(Override.class);
    }
}
