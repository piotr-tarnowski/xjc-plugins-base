package com.devontrain.jaxb.plugins;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.devontrain.jaxb.common.XJCPlugin;
import com.devontrain.jaxb.common.XJCPluginBase;
import com.devontrain.jaxb.common.XJCPluginProperty;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JClassContainer;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMods;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

@XJCPlugin(name = "Xclean")
public class CleanXJCPlugin extends XJCPluginBase
{
    @XJCPluginProperty(defaultValue = "true")
    private boolean factories = false;

    @XJCPluginProperty(defaultValue = "true")
    private boolean annotations = false;

    @Override
    public boolean run( Outline omodel, Options opt, ErrorHandler errorHandler ) throws SAXException {

        Iterator<? extends ClassOutline> iterator = omodel.getClasses().iterator();
        while( iterator.hasNext() ){
            ClassOutline classOutline = iterator.next();

            JDefinedClass implClass = classOutline.implClass;
            JClassContainer parentContainer = implClass.parentContainer();
            if ( parentContainer instanceof JPackage ){
                JPackage pck = (JPackage) parentContainer;
                if ( factories ){
                    final JDefinedClass objectFactoryClass = pck._getClass( "ObjectFactory" );
                    if ( objectFactoryClass != null ){
                        pck.remove( objectFactoryClass );
                    }
                }
                if ( annotations ){
                    for( Iterator<JDefinedClass> iter = pck.classes(); iter.hasNext(); ){
                        if ( iter.next().equals( implClass ) ){
                            iter.remove();
                            break;
                        }
                    }
                    pck.remove( implClass );

                    try{
                        JDefinedClass _implClass = omodel.getModel().codeModel._class( implClass.fullName() );
                        Map<String, JFieldVar> fields = implClass.fields();
                        Iterator<Entry<String, JFieldVar>> fiterator = fields.entrySet().iterator();
                        while( fiterator.hasNext() ){
                            Entry<String, JFieldVar> entry = fiterator.next();
                            JFieldVar value = entry.getValue();
                            JMods mods = value.mods();
                            _implClass.field( mods.getValue(), value.type(), entry.getKey() );
                        }
                        Collection<JMethod> methods = implClass.methods();
                        Iterator<JMethod> miterator = methods.iterator();
                        while( miterator.hasNext() ){
                            JMethod method = miterator.next();
                            JMethod m = _implClass.method( method.mods().getValue(), method.type(), method.name() );
                            List<JVar> params = method.params();
                            for( JVar param : params ){
                                m.param( param.mods().getValue(), param.type(), param.name() );
                            }
                            m.body().add( (JStatement) method.body().getContents().iterator().next() );
                        }

                        Iterator<JDefinedClass> citerator = implClass.classes();
                        while( citerator.hasNext() ){
                            JDefinedClass c = citerator.next();
                            _implClass._class( c.mods().getValue(), c.name(), c.getClassType() );
                        }
                        try{
                            Field implClasField = ClassOutline.class.getField( "implClass" );
                            implClasField.setAccessible( true );
                            implClasField.set( classOutline, _implClass );
                            implClasField.setAccessible( false );
                        }catch( NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e ){
                            e.printStackTrace();
                        }

                    }catch( JClassAlreadyExistsException e ){
                        e.printStackTrace();
                    }
                }
            }else if ( annotations && parentContainer instanceof JDefinedClass ){
                JDefinedClass parentClass = (JDefinedClass) parentContainer;
                for( Iterator<JDefinedClass> iter = parentClass.classes(); iter.hasNext(); ){
                    if ( iter.next().equals( implClass ) ){
                        iter.remove();
                        break;
                    }
                }
            }
        }
        return true;
    }
}
