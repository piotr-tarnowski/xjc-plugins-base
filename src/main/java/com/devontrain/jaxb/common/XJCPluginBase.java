package com.devontrain.jaxb.common;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.model.CCustomizations;
import com.sun.tools.xjc.model.CElementInfo;
import com.sun.tools.xjc.model.CEnumLeafInfo;
import com.sun.tools.xjc.model.CPluginCustomization;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.ElementOutline;
import com.sun.tools.xjc.outline.EnumOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.tools.xjc.reader.Ring;
import com.sun.tools.xjc.reader.xmlschema.BGMBuilder;

public abstract class XJCPluginBase extends Plugin
{
    private final String option = "-" + getOptionName();
    private final String pattern = option + "(:((.*?)=)?(.*?))$";
    private final Pattern argsExtractor = Pattern.compile( pattern );
    private BGMBuilder bgmBuilder;

    protected BGMBuilder getBgmBuilder() {
        return bgmBuilder;
    }

    protected void setBgmBuilder( BGMBuilder bgmBuilder ) {
        this.bgmBuilder = bgmBuilder;
    }

    @Override
    public String getOptionName() {
        return getClass().getAnnotation( XJCPlugin.class ).name();
    }

    @Override
    public String getUsage() {
        StringBuilder sb = new StringBuilder();
        sb.append( "  " );
        sb.append( getOptionName() );
        sb.append( " " );
        for( Method method : getClass().getDeclaredMethods() ){
            Class<?>[] parameterTypes = method.getParameterTypes();
            final XJCPluginProperty annotation = method.getAnnotation( XJCPluginProperty.class );
            if ( annotation != null && parameterTypes.length == 1 ){
                final Class<?> type = parameterTypes[0];
                String name = XJCPluginUtil.nameFromSetter( method.getName() );
                XJCPluginUtil.describeArgument( sb, getOptionName(), name, type, annotation );
            }
        }
        for( Field field : getClass().getDeclaredFields() ){
            final XJCPluginProperty annotation = field.getAnnotation( XJCPluginProperty.class );
            if ( annotation != null ){
                final Class<?> type = field.getType();
                XJCPluginUtil.describeArgument( sb, getOptionName(), field.getName(), type, annotation );
            }
        }
        return sb.toString();
    }

    protected final Map<Class<Enum<?>>, Enum<?>[]> customizations;
    protected final Map<String, Collection<Class<Enum<?>>>> uris;
    private Collection<GlobalCustomizationHandler<? extends Enum<?>>> globalCustomizationHandlers = new LinkedList<>();
    private Collection<ElementCustomizationHandler<? extends Enum<?>>> elementCustomizationHandlers = new LinkedList<>();
    private Collection<ClassCustomizationHandler<? extends Enum<?>>> classCustomizationHandlers = new LinkedList<>();
    private Collection<FieldCustomizationHandler<? extends Enum<?>>> fieldCustomizationHandlers = new LinkedList<>();
    private Collection<EnumCustomizationHandler<? extends Enum<?>>> enumCustomizationHandlers = new LinkedList<>();

    protected XJCPluginBase() {
        //for initialization

        Map<Class<Enum<?>>, Enum<?>[]> custs = new LinkedHashMap<Class<Enum<?>>, Enum<?>[]>();
        Map<String, Collection<Class<Enum<?>>>> tmp = new LinkedHashMap<String, Collection<Class<Enum<?>>>>();
        Class<?>[] classes = getClass().getDeclaredClasses();
        for( Class<?> c : classes ){
            XJCPluginCustomizations annotation = (XJCPluginCustomizations) c.getAnnotation( XJCPluginCustomizations.class );
            if ( annotation != null ){
                if ( c.isEnum() ){
                    registerCustomization( annotation, custs, tmp, c );
                }else if ( c.isInterface() ){
                    for( Class<?> ci : c.getClasses() ){
                        registerCustomization( annotation, custs, tmp, ci );
                    }
                }
            }
        }
        customizations = Collections.unmodifiableMap( custs );
        //        System.out.println(customizations);
        uris = Collections.unmodifiableMap( tmp );
        //        System.out.println(uris);
    }

    private void registerCustomization( XJCPluginCustomizations annotation, Map<Class<Enum<?>>, Enum<?>[]> custs, Map<String, Collection<Class<Enum<?>>>> tmp, Class<?> c ) {
        @SuppressWarnings("unchecked")
        final Class<Enum<?>> ec = (Class<Enum<?>>) c;
        final Enum<?>[] enumConstants = getEnumConstants( ec );
        Arrays.sort( enumConstants );
        custs.put( ec, enumConstants );
        Collection<Class<Enum<?>>> collection = tmp.get( annotation.uri() );
        if ( collection == null ){
            collection = new LinkedList<>();
            tmp.put( annotation.uri(), collection );
        }
        collection.add( ec );
    }

    @Override
    public int parseArgument( Options opt, String[] args, int i ) throws BadCommandLineException, IOException {
        if ( args[i].equals( option ) ){
            int count = 1;
            arguments: while( i < args.length ){
                String arg = args[i];
                i++;

                Matcher matcher = argsExtractor.matcher( arg );
                if ( matcher.find() ){
                    String name = matcher.group( 3 );
                    String value = null;
                    if ( name == null ){
                        name = matcher.group( 4 );
                    }else{
                        value = matcher.group( 4 );
                    }
                    count++;

                    try{
                        String method = XJCPluginUtil.setterForName( name );
                        for( Method m : getClass().getDeclaredMethods() ){
                            XJCPluginProperty annotation = m.getAnnotation( XJCPluginProperty.class );
                            if ( annotation != null ){
                                if ( value == null ){
                                    value = annotation.defaultValue();
                                }
                                if ( m.getName().equals( method ) ){
                                    Class<?>[] parameterTypes = m.getParameterTypes();
                                    if ( parameterTypes.length == 1 ){
                                        Class<?> type = parameterTypes[0];
                                        Object tmp = XJCPluginUtil.convert( value, type );
                                        if ( tmp != null ){
                                            boolean accessible = m.isAccessible();
                                            m.setAccessible( true );
                                            m.invoke( this, tmp );
                                            m.setAccessible( accessible );
                                            continue arguments;
                                        }
                                    }
                                }
                            }
                        }

                        Field field = getClass().getDeclaredField( name );
                        XJCPluginProperty annotation = field.getAnnotation( XJCPluginProperty.class );
                        if ( annotation != null ){
                            if ( value == null ){
                                value = annotation.defaultValue();
                            }
                            Class<?> type = field.getType();
                            Object tmp = XJCPluginUtil.convert( value, type );
                            if ( tmp != null ){
                                boolean accessible = field.isAccessible();
                                field.setAccessible( true );
                                field.set( this, tmp );
                                field.setAccessible( accessible );
                                continue arguments;
                            }
                        }

                    }catch( Exception e ){
                        e.printStackTrace();
                    }

                }
            }
            return count;
        }
        return 0;
    }

    @Override
    public List<String> getCustomizationURIs() {
        return Collections.unmodifiableList( new LinkedList<>( uris.keySet() ) );
    }

    @Override
    public boolean isCustomizationTagName( String nsUri, String localName ) {
        Collection<Class<Enum<?>>> collection = uris.get( nsUri );
        if ( collection != null ){
            for( Class<Enum<?>> clazz : collection ){
                return valueOf( clazz, localName ) != null;
            }
        }
        return false;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <T extends Enum> T[] getEnumConstants( final Class<T> clazz ) {
        T[] enumConstants = clazz.getEnumConstants();
        if ( clazz.isEnum() && enumConstants == null ){// JAVA BUG!!!!
            try{
                final Method method = clazz.getMethod( "values", new Class<?>[] {} );
                enumConstants = (T[]) method.invoke( null, new Object[] {} );
            }catch( final Exception e ){
                e.printStackTrace();
            }
        }
        return enumConstants;
    }

    private static Enum<?> valueOf( Class<Enum<?>> en, String value ) {
        Enum<?>[] values = getEnumConstants( en );
        for( Enum<?> e : values ){
            if ( e.name().equals( value ) ){
                return e;
            }
        }
        return null;
    }

    protected final void handleDeclaredCustomizations( final Outline outline, final Options opt, final ErrorHandler errorHandler ) throws SAXException {
        final Model model = outline.getModel();
        handleCustomizationsInner( outline, opt, errorHandler, model.getCustomizations(), getGlobalCustomizationHandlers() );

        for( CElementInfo elementInfo : model.getAllElements() ){
            final ElementOutline elementOutline = outline.getElement( elementInfo );
            if ( elementOutline == null ){
                continue;
            }
            final CElementInfo target = elementOutline.target;
            handleCustomizationsInner( elementOutline, opt, errorHandler, target.getCustomizations(), getElementCustomizationHandlers() );
        }

        Collection<? extends ClassOutline> classes = outline.getClasses();
        for( ClassOutline classOutline : classes ){
            final CClassInfo target = classOutline.target;
            handleCustomizationsInner( classOutline, opt, errorHandler, target.getCustomizations(), getClassCustomizationHandlers() );

            for( final FieldOutline fieldOutline : classOutline.getDeclaredFields() ){
                final CPropertyInfo propertyInfo = fieldOutline.getPropertyInfo();
                handleCustomizationsInner( fieldOutline, opt, errorHandler, propertyInfo.getCustomizations(), getFieldCustomizationHandlers() );
            }
        }

        for( EnumOutline enumOutline : outline.getEnums() ){
            final CEnumLeafInfo target = enumOutline.target;
            handleCustomizationsInner( enumOutline, opt, errorHandler, target.getCustomizations(), getEnumCustomizationHandlers() );
        }

    }

    protected final Collection<GlobalCustomizationHandler<? extends Enum<?>>> getGlobalCustomizationHandlers() {
        return globalCustomizationHandlers;
    }

    protected final Collection<ElementCustomizationHandler<? extends Enum<?>>> getElementCustomizationHandlers() {
        return elementCustomizationHandlers;
    }

    protected final Collection<ClassCustomizationHandler<? extends Enum<?>>> getClassCustomizationHandlers() {
        return classCustomizationHandlers;
    }

    protected final Collection<FieldCustomizationHandler<? extends Enum<?>>> getFieldCustomizationHandlers() {
        return fieldCustomizationHandlers;
    }

    protected final Collection<EnumCustomizationHandler<? extends Enum<?>>> getEnumCustomizationHandlers() {
        return enumCustomizationHandlers;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private final <O> void handleCustomizationsInner( final O outline, final Options opt, final ErrorHandler errorHandler, final CCustomizations customizations, final Collection<? extends CustomizationHandler> handlers ) throws SAXException {
        //        System.out.println( outline + " --> " + handlers );
        for( CustomizationHandler handler : handlers ){
            Class enumType = handler.getEnumType();
            XJCPluginCustomizations annotation = (XJCPluginCustomizations) enumType.getAnnotation( XJCPluginCustomizations.class );
            if ( annotation == null ){
                Class declaringClass = enumType.getDeclaringClass();
                if ( declaringClass != null ){
                    annotation = (XJCPluginCustomizations) declaringClass.getAnnotation( XJCPluginCustomizations.class );
                }
            }
            String uri = annotation.uri();
            for( CPluginCustomization cp : customizations ){
                if ( uri.equals( cp.element.getNamespaceURI() ) ){
                    Enum value = valueOf( enumType, cp.element.getLocalName() );
                    if ( value != null ){
                        cp.markAsAcknowledged();
                        for( Field field : enumType.getFields() ){
                            if ( field.getName().equals( value.name() ) ){

                                AllowedAttributes allowedAttributes = field.getAnnotation( AllowedAttributes.class );
                                if ( allowedAttributes == null ){//disable checking attributes
                                    continue;
                                }

                                Collection<String> a_attrs = extractAnnotationValues( allowedAttributes, false );
                                Collection<String> r_attrs = extractAnnotationValues( allowedAttributes, true );

                                boolean noAttributesRequired = a_attrs.size() + r_attrs.size() == 0;
//                                System.out.println("noAttributesRequired:\t" + noAttributesRequired);
                                NamedNodeMap attributes = cp.element.getAttributes();
                                for( int i = 0; i < attributes.getLength(); i++ ){
                                    Node item = attributes.item( i );
                                    final String namespaceURI = item.getNamespaceURI();
                                    final String localName = item.getLocalName();
                                    if ( noAttributesRequired && namespaceURI == null || uri.equals( namespaceURI ) ){
                                        errorHandler.warning( new SAXParseException( "compiler was unable to honor this " + cp.element.getPrefix() + ":" + value.name() + " customization. Uwanted attriubte named: " + localName + " has been found.", cp.locator ) );
                                        continue;
                                    }
                                    if ( r_attrs.contains( localName ) ){
                                        r_attrs.remove( localName );
                                        String val = item.getTextContent();
                                        if ( val == null || "".equals( val.trim() ) ){
                                            errorHandler.error( new SAXParseException( "Attribute " + localName + " value for " + cp.element.getPrefix() + ":" + value.name() + " customization is missing or empty.", cp.locator ) );
                                        }else{
                                            continue;
                                        }
                                    }else if ( a_attrs.contains( localName ) ){
                                        a_attrs.remove( localName );
                                        continue;
                                    }else if ( namespaceURI == null || uri.equals( namespaceURI ) ){
                                        errorHandler.warning( new SAXParseException( "Attribute " + localName + " for " + cp.element.getPrefix() + ":" + value.name() + " customization is unknown.", cp.locator ) );
                                    }
                                }
                                if ( r_attrs.size() > 0 ){
                                    errorHandler.error( new SAXParseException( "Required attributes " + r_attrs + " for " + cp.element.getPrefix() + ":" + value.name() + " customization are missing.", cp.locator ) );
                                }
                            }
                        }
                        handler.handle( outline, value, cp );
                    }
                }
            }
        }
    }

    private <T extends Annotation> Collection<String> extractAnnotationValues( AllowedAttributes allowedAttributes, boolean requiredOnly ) {
        Collection<String> attritubtes = new LinkedList<>();
        Class<?>[] classes = allowedAttributes.value();
        for( Class<?> c : classes ){
            Field[] fields = c.getFields();
            for( Field field : fields ){
                if ( field.getType() == String.class ){
                    if ( field.getAnnotation( NotAttribute.class ) == null ){
                        final int modifiers = field.getModifiers();
//                        System.out.println( c.getName() + "." + field.getName() + " " + modifiers );
                        if ( modifiers == Modifier.STATIC + Modifier.FINAL + Modifier.PUBLIC ){
                            Required required = field.getAnnotation( Required.class );
                            if ( required == null ){
                                required = c.getAnnotation( Required.class );
                            }
                            if ( ( requiredOnly && required != null ) || ( !requiredOnly && required == null ) ){
                                try{
                                    attritubtes.add( (String) field.get( null ) );
                                }catch( Exception e ){
                                    throw new IllegalStateException( e );//should never happen
                                }
                            }
                        }
                    }else if ( field.getAnnotation( Required.class ) != null ){
                        throw new IllegalStateException( "Constant cannot be required and not attribute at the same time. Please check annotation on: " + c.getName() + "." + field.getName() );
                    }
                }
            }
        }
        return attritubtes;
    }

    @Override
    public void postProcessModel( Model model, ErrorHandler errorHandler ) {
        super.postProcessModel( model, errorHandler );
        this.setBgmBuilder( Ring.get( BGMBuilder.class ) );// for adding new classes (?interfances)
    }

}