package com.devontrain.jaxb.plugins;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.devontrain.jaxb.common.XJCPlugin;
import com.devontrain.jaxb.common.XJCPluginBase;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.model.CCustomizations;
import com.sun.tools.xjc.model.CElementInfo;
import com.sun.tools.xjc.model.CPluginCustomization;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.ElementOutline;
import com.sun.tools.xjc.outline.EnumOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;

/*
 * 1. Remove unnecessary classes
 * 2. Add new classes
 * 3. Clear/modify classes in outline 
 */
@XJCPlugin(name = "Xcust")
public class CustomizationXJCPlugin extends XJCPluginBase
{

    private static final String NAMESPACE_URI = "http://common.jaxb.devontrain.com/plugin/oxm-generator";

    @Override
    public List<String> getCustomizationURIs() {
        return Arrays.asList( NAMESPACE_URI );
    }

    @Override
    public boolean isCustomizationTagName( String nsUri, String localName ) {
        System.out.println( nsUri + ":" + localName + "-\t" + getCustomizationURIs().contains( nsUri ) );
        return getCustomizationURIs().contains( nsUri );
    }

    @Override
    public boolean run( Outline omodel, Options opt, ErrorHandler errorHandler ) throws SAXException {
        System.out.println( "runs.." );
        CCustomizations mCustomizations = omodel.getModel().getCustomizations();
        for(CPluginCustomization cp : mCustomizations){
            System.out.println(cp.element.getNamespaceURI() + ":" + cp.element.getLocalName());
            if(isCustomizationTagName( cp.element.getNamespaceURI(), cp.element.getLocalName() )){
                cp.markAsAcknowledged();
            }
        }
        System.out.println( "..." );
        for( CElementInfo elementInfo : omodel.getModel().getAllElements() ){
            final ElementOutline elementOutline = omodel.getElement( elementInfo );
            if ( elementOutline != null ){
                final CCustomizations customizations = elementOutline.target.getCustomizations();
                CPluginCustomization c = customizations.find( NAMESPACE_URI, "alias" );
                for(CPluginCustomization cp : customizations){
                    System.out.println(cp.element.getNamespaceURI() + ":" + cp.element.getLocalName());
                }
                if ( c == null ){
                    System.out.println( "el " + elementInfo.fullName() + ":" + "false\t" + customizations );
                    continue; // no customization --- nothing to inject here
                }else{
                    System.out.println( "el " + elementInfo.fullName() + ":" + "true\t" + customizations );
                }

                c.markAsAcknowledged();
            }
        }

        Collection<? extends ClassOutline> classes = omodel.getClasses();
        for( ClassOutline co : classes ){
            final CClassInfo target = co.target;
            final CCustomizations customizations = target.getCustomizations();
            for(CPluginCustomization cp : customizations){
                System.out.println(cp.element.getNamespaceURI() + ":" + cp.element.getLocalName());
            }
            for (final FieldOutline fieldOutline : co.getDeclaredFields()) {
                final CPropertyInfo propertyInfo = fieldOutline.getPropertyInfo();
                final CCustomizations customizations2 = propertyInfo.getCustomizations();
                CPluginCustomization c2 = customizations2.find( NAMESPACE_URI, "alias" );
//                for(CPluginCustomization cp : customizations2){
//                    System.out.println(cp.element.getNamespaceURI() + ":" + cp.element.getLocalName());
//                }
                if ( c2 == null ){
                    System.out.println( "pr " + target.getName() + ":" + "false\t" + customizations2 );
                    continue; // no customization --- nothing to inject here
                }else{
                    System.out.println( "pr " + target.getName()+"."+ fieldOutline.getPropertyInfo().getName( false ) + ":" + "true\t" + customizations2);
                }
                c2.markAsAcknowledged();
            }
            CPluginCustomization c = customizations.find( NAMESPACE_URI, "alias" );
            if ( c == null ){
                //                Iterator<CPluginCustomization> iterator = customizations.iterator();
                //                while(iterator.hasNext()){
                //                    iterator.next().locator.get
                //                }
                System.out.println( "c " + target.getName() + ":" + "false\t" + customizations);
                continue; // no customization --- nothing to inject here
            }else{
                System.out.println( "c " + target.getName() + ":" + "true\t" + customizations );
            }

            c.markAsAcknowledged();
            
        }

        for( EnumOutline enumOutline : omodel.getEnums() ){

            final CCustomizations customizations = enumOutline.target.getCustomizations();
            CPluginCustomization c = customizations.find( NAMESPACE_URI, "alias" );
            for(CPluginCustomization cp : customizations){
                System.out.println(cp.element.getNamespaceURI() + ":" + cp.element.getLocalName());
            }
            if ( c == null ){
                System.out.println( "en " + enumOutline.target.fullName() + ":" + "false\t" + customizations );
                continue; // no customization --- nothing to inject here
            }else{
                System.out.println( "en " + enumOutline.target.fullName() + ":" + "true\t" + customizations );
            }

            c.markAsAcknowledged();
        }

        System.out.println( "ends..." );
        return true;
    }

}
