package com.devontrain.jaxb.plugins;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.devontrain.jaxb.common.XJCPlugin;
import com.devontrain.jaxb.common.XJCPluginBase;
import com.devontrain.jaxb.common.XJCPluginProperty;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import com.sun.codemodel.JClassContainer;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFormatter;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JStatement;
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
public class AddXJCPlugin extends XJCPluginBase
{
    @XJCPluginProperty(defaultValue = "true")
    private boolean impls = false;

    @XJCPluginProperty(defaultValue = "true")
    private boolean ifaces = false;

    @XJCPluginProperty(defaultValue = "Impl")
    private String suffix = "Impl";

    @Override
    public boolean run( Outline omodel, Options opt, ErrorHandler errorHandler ) throws SAXException {

        Ring ring = Ring.begin();
        try{
            Ring.add( getBgmBuilder() );
            Map<String, JDefinedClass> factories = new HashMap<>();
            Map<String, JDefinedClass> ifaceClasses = new HashMap<>();
            Map<String, JDefinedClass> implClasses = new HashMap<>();

            Iterator<? extends ClassOutline> iterator = omodel.getClasses().iterator();
            while( iterator.hasNext() ){
                ClassOutline classOutline = iterator.next();
                JDefinedClass implClass = classOutline.implClass;
                
                final JClassContainer classContainer = implClass.parentContainer();
                if ( classContainer instanceof JPackage ){
                    JPackage pck = (JPackage) classContainer;
                    if ( !factories.containsKey( pck.name() ) ){
                        JDefinedClass factory = pck._getClass( "ObjectFactory" );
                        factories.put( pck.name(), factory );
                    }
                }
                
                final String name = implClass.name();
                if ( impls ){
                    setName( implClass, name, suffix );
                    implClasses.put( implClass.name(), implClass );
                }else{
                    removeClassFromCodeModel( implClass );
                }
                if ( ifaces ){
                    JDefinedClass ifaceClass = classContainer._interface( name );
                    implClass._implements( ifaceClass );
                    ifaceClasses.put( ifaceClass.name(), ifaceClass );
                }
            }
            
            handleFactories( factories, ifaceClasses, implClasses );

        }catch( /*JClassAlreadyExists*/Exception e1 ){
            e1.printStackTrace();
        }finally{
            Ring.end( ring );
        }

        return true;
    }

    private void setName( final JDefinedClass jDefinedClass, final String name, final String suffix ) {
        final String newName = name + suffix;
        setFieldValue( jDefinedClass, "name", newName );

        final JClassContainer parent = jDefinedClass.parentContainer();
        final Map<String, JDefinedClass> classes = getFieldValue( parent, "classes" );

        if ( parent instanceof JDefinedClass ){
            boolean isCaseSensitiveSystem = classes.remove( name.toUpperCase() ) != null;
            if ( isCaseSensitiveSystem ){
                classes.put( newName.toUpperCase(), jDefinedClass );
            }else{
                classes.remove( name );
                classes.put( newName, jDefinedClass );
            }
        }else if ( parent instanceof JPackage ){

            Map<String, JDefinedClass> upperCaseClassMap = getFieldValue( parent, "upperCaseClassMap" );

            boolean isCaseSensitiveSystem = upperCaseClassMap != null && upperCaseClassMap.remove( name.toUpperCase() ) != null;
            classes.remove( name );
            if ( isCaseSensitiveSystem ){
                upperCaseClassMap.put( newName.toUpperCase(), jDefinedClass );
            }
            classes.put( newName, jDefinedClass );

        }

    }

    private static final Map<String, Field> fields = new HashMap<>();

    private Field getField( Class<?> clazz, String name ) {
        try{
            String key = clazz.getCanonicalName() + "." + name;
            Field field = fields.get( key );
            if ( field == null ){
                field = clazz.getDeclaredField( name );
                field.setAccessible( true );
                fields.put( key, field );
            }
            return field;
        }catch( NoSuchFieldException | SecurityException e ){
            e.printStackTrace();
        }
        return null;
    }

    private <T> T getFieldValue( Object o, String name ) {
        if ( o != null ){
            Field field = getField( o.getClass(), name );
            if ( field != null ){
                try{
                    return (T) field.get( o );
                }catch( IllegalArgumentException | IllegalAccessException e ){
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private <T> void setFieldValue( Object o, String name, T value ) {
        if ( o != null ){
            Field field = getField( o.getClass(), name );
            if ( field != null ){
                try{
                    field.set( o, value );
                }catch( IllegalArgumentException | IllegalAccessException e ){
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleFactories( Map<String, JDefinedClass> factories, Map<String, JDefinedClass> ifaceClasses, Map<String, JDefinedClass> implClasses ) {
        Pattern findInitialziation = Pattern.compile( "new\\s+([^\\s\\(\\<]+)" );
        for( JDefinedClass factory : factories.values() ){
            if ( factory == null ){
                continue;
            }
            Collection<JMethod> methods = factory.methods();
            Iterator<JMethod> mIterator = methods.iterator();
            while( mIterator.hasNext() ){
                JMethod method = mIterator.next();
                for( Object content : method.body().getContents() ){
                    if ( content instanceof JStatement ){
                        JStatement statement = (JStatement) content;
                        StringWriter sw = new StringWriter();
                        JFormatter f = new JFormatter( new PrintWriter( sw ), "" );
                        statement.state( f );
                        Matcher matcher = findInitialziation.matcher( sw.toString() );
                        while( matcher.find() ){
                            String fullName = matcher.group( 1 );
                            JDefinedClass ifaceClass = ifaceClasses.get( fullName );
                            JDefinedClass implClass = implClasses.get( fullName );
                            if ( ifaceClass == null ){
                                if ( implClass == null ){
                                    //TODO: Remove method
                                    System.err.println( "Remove method (1)" );
                                    mIterator.remove();
                                }else{
                                    //NOTHING TO DO!!!
                                    System.err.println( "NOTHING TO DO!!!" );
                                }
                            }else{
                                if ( implClass == null ){
                                    //TODO: Remove method\
                                    System.err.println( "Remove method (2)" );
                                    mIterator.remove();
                                }else{
                                    //TODO: Replace initialization
                                    System.err.println( "RReplace initialization" );
                                }

                            }
                        }
                    }
                }
            }

            if ( factory.methods().size() == 0 ){
                Iterator<JDefinedClass> classes = factory.parentContainer().classes();
                while( classes.hasNext() ){
                    if ( classes.next() == factory ){
                        classes.remove();
                        break;
                    }
                }
            }
        }
    }

    private void removeClassFromCodeModel( JDefinedClass implClass ) {
        JClassContainer parentContainer = implClass.parentContainer();
        for( Iterator<JDefinedClass> iter = parentContainer.classes(); iter.hasNext(); ){
            if ( iter.next().equals( implClass ) ){
                iter.remove();
                break;
            }
        }
        //        if ( parentContainer.isPackage() ){
        //            JPackage pck = (JPackage) parentContainer;
        //            pck.remove( implClass );
        //        }
    }
}
