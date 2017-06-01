package com.devontrain.jaxb.common;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/*package*/ final class XJCPluginUtil
{
    private XJCPluginUtil() {

    }

    private final static Map<Class<?>, Class<?>> primitives = new HashMap<Class<?>, Class<?>>();
    static{
        primitives.put( boolean.class, Boolean.class );
        primitives.put( byte.class, Byte.class );
        primitives.put( short.class, Short.class );
        primitives.put( char.class, Character.class );
        primitives.put( int.class, Integer.class );
        primitives.put( long.class, Long.class );
        primitives.put( float.class, Float.class );
        primitives.put( double.class, Double.class );
        primitives.put( Boolean.class, Boolean.class );
        primitives.put( Byte.class, Byte.class );
        primitives.put( Short.class, Short.class );
        primitives.put( Character.class, Character.class );
        primitives.put( Integer.class, Integer.class );
        primitives.put( Long.class, Long.class );
        primitives.put( Float.class, Float.class );
        primitives.put( Double.class, Double.class );
        primitives.put( String.class, String.class );
    }

    /*package*/ static String nameFromSetter( String setter ) {
        char[] chars = setter.toCharArray();
        chars[3] = Character.toLowerCase( chars[3] );
        return new String( chars, 3, chars.length - 3 );
    }

    /*package*/ static String setterForName( String name ) {
        char[] chars = name.toCharArray();
        chars[0] = Character.toUpperCase( chars[0] );
        StringBuilder sb = new StringBuilder();
        sb.append( "set" );
        sb.append( chars );
        String method = sb.toString();
        return method;
    }

    /*package*/ static Object convert( String value, Class<?> type ) {
        Object tmp = null;
        if ( String.class == type ){
            tmp = value;
        }else if ( type.isEnum() ){
            tmp = Enum.valueOf( (Class<Enum>) type, value );
        }else{
            Class<?> ptype = primitives.get( type );
            if ( ptype != null ){
                try{
                    Constructor<?> constructor = ptype.getConstructor( new Class[] { String.class } );
                    tmp = constructor.newInstance( value );
                }catch( Exception e ){
                    System.err.println( ptype + " " + value );
                    e.printStackTrace();
                }
            }
        }
        return tmp;
    }
    
    /*package*/ static void describeArgument( StringBuilder sb, String operation, String name, final Class<?> type, final XJCPluginProperty annotation ) {
        sb.append( operation );
        sb.append( ":" );
        sb.append( name );
        sb.append( "[=" );
        sb.append( type.getSimpleName().toLowerCase() );
        sb.append( "(default" );
        sb.append( annotation.defaultValue() );
        sb.append( ")]" );
        sb.append( " " );
    }

}
