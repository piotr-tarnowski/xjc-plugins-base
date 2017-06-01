package com.devontrain.jaxb.common;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
import com.sun.tools.xjc.reader.Ring;

/*
 * 1. Remove unnecessary classes
 * 2. Add new classes
 * 3. Clear/modify classes in outline 
 */
@XJCPlugin(name = "Xadd")
public class TemplateXJCPlugin extends XJCPluginBase
{
    @XJCPluginProperty(defaultValue = "true")
    private boolean impls = false;

    @XJCPluginProperty(defaultValue = "true")
    private boolean ifaces = false;

    @XJCPluginProperty(defaultValue = "true")
    private boolean tests = false;

    @Override
    public boolean run( Outline omodel, Options opt, ErrorHandler errorHandler ) throws SAXException {

        // Collection<CClassInfo> classes = new LinkedList<CClassInfo>();
        Iterator<? extends ClassOutline> iterator = omodel.getClasses().iterator();

        Ring ring = Ring.begin();
        try{
            Ring.add( getBgmBuilder() );
            //            Model model = omodel.getModel();
            //            for( Iterator<XSComplexType> iterator2 = model.schemaComponent.iterateComplexTypes(); iterator2.hasNext(); ){
            //                XSComplexType complex = iterator2.next();
            //            }
            while( iterator.hasNext() ){
                ClassOutline classOutline = iterator.next();

                JDefinedClass implClass = classOutline.implClass;
                
                if(ifaces){
                    
                    if(impls){
                        
                    }
                }
                
                JClassContainer parentContainer = implClass.parentContainer();
                if ( parentContainer instanceof JPackage ){
                    JPackage pck = (JPackage) parentContainer;
                    for( Iterator<JDefinedClass> iter = pck.classes(); iter.hasNext(); ){
                        if ( iter.next().equals( implClass ) ){
                            iter.remove();
                            break;
                        }
                    }
                    pck.remove( implClass );
                    /* CClassInfo info = *///omodel.getModel().beans().remove( classOutline.target );

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
                    // we back to the start point
                    //                        final CClassInfo target = classOutline.target;
                    //                        CClassInfo clazz = new CClassInfo( model, implClass._package(), implClass.name() + "2", target.getLocator(), target.getTypeName(), target.getElementName(), target.getSchemaComponent(), target.getCustomizations() );
                    //                        classes.add( clazz );
                }else if ( parentContainer instanceof JDefinedClass ){
                    JDefinedClass parentClass = (JDefinedClass) parentContainer;
                    for( Iterator<JDefinedClass> iter = parentClass.classes(); iter.hasNext(); ){
                        if ( iter.next().equals( implClass ) ){
                            iter.remove();
                            break;
                        }
                    }
                }
                // iterator.remove();
            }
        }finally{
            Ring.end( ring );
        }
        // we back to the start point -but this is a code for adding new classes
        //        System.out.println();
        //        for( CClassInfo clazz : classes ){
        //            clazz.setUserSpecifiedImplClass( clazz.fullName().replaceAll( "2", "" ) );
        //            ClassOutline coutline = omodel.getClazz( clazz );//this unfortunately
        //            //adds annotations again.... :/
        //        }
        // this doesn't work at all
        // ((Collection)omodel.getClasses()).addAll( classes );

        return true;
    }
}
