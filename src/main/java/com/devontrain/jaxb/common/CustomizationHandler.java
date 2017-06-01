package com.devontrain.jaxb.common;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.xml.sax.SAXException;

import com.sun.tools.xjc.model.CPluginCustomization;

abstract class CustomizationHandler<OUTLINE, CUSTOMIZATION extends Enum<?>>
{
    private Class<CUSTOMIZATION> enumType = null;

    protected CustomizationHandler() {

    }

    @SuppressWarnings("unchecked")
    public Class<CUSTOMIZATION> getEnumType() {
        if ( enumType == null ){
            Type type = getClass().getGenericSuperclass();
            enumType = (Class<CUSTOMIZATION>) ( (ParameterizedType) type ).getActualTypeArguments()[0];
        }
        return enumType;
    }

    public abstract void handle( OUTLINE outline, CUSTOMIZATION cust, CPluginCustomization cp ) throws SAXException;

}
