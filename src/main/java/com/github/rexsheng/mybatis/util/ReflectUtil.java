package com.github.rexsheng.mybatis.util;

import java.beans.Introspector;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.github.rexsheng.mybatis.annotation.ColumnIgnore;
import com.github.rexsheng.mybatis.converter.Converter;
import com.github.rexsheng.mybatis.converter.DtoConverter;
import com.github.rexsheng.mybatis.core.SFunction;

public class ReflectUtil {
	
	private static DtoConverter converter=new DtoConverter();
	
	private static final Pattern GET_PATTERN = Pattern.compile("get[A-Z].*");
    private static final Pattern IS_PATTERN = Pattern.compile("is[A-Z].*");
    
    private static final Map<Class<?>, List<Field>> CLASS_FIELD_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 利用反射获取指定对象的指定属性
     *
     * @param obj       目标对象
     * @param fieldName 目标属性
     * @return 目标属性的值
     */
    public static Object getFieldValue(Object obj, String fieldName) {
        Object result = null;
        Field field = ReflectUtil.getField(obj, fieldName);
        if (field != null) {
            field.setAccessible(true);
            try {
                result = field.get(obj);
            } catch (IllegalArgumentException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return result;
    }

    /**
     * 利用反射获取指定对象里面的指定属性
     *
     * @param obj       目标对象
     * @param fieldName 目标属性
     * @return 目标字段
     */
    private static Field getField(Object obj, String fieldName) {
        Field field = null;
        for (Class<?> clazz = obj.getClass(); clazz != Object.class; clazz = clazz.getSuperclass()) {
            try {
                field = clazz.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {
                // 这里不用做处理，子类没有该字段可能对应的父类有，都没有就返回null。
            }
        }
        return field;
    }
    
    public static Field getClassField(Class<?> clazz, String fieldName) {
        Field field = null;
        while(clazz!=Object.class) {
        	try {
                field = clazz.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {
                // 这里不用做处理，子类没有该字段可能对应的父类有，都没有就返回null。
            }
        	clazz = clazz.getSuperclass();
        }
        return field;
    }
    
    public static List<Field> getDeclaredFields(Class<?> target) {
		List<Field> fieldList = CLASS_FIELD_CACHE.get(target);
        if (fieldList==null || fieldList.isEmpty()) {
            synchronized (CLASS_FIELD_CACHE) {
            	fieldList = getDeclaredFieldsWithoutCache(target);
                CLASS_FIELD_CACHE.put(target, fieldList);
            }
        }
        return fieldList;
	}
    
    public static List<Field> getDeclaredFieldsWithoutCache(Class<?> target) {
		List<Field> fieldList = new ArrayList<>();
		Class<?> tempClass = target;
		while (tempClass != null && tempClass != Object.class) {//当父类为null的时候说明到达了最上层的父类(Object类).
			List<Field> currentList=new ArrayList<>();
			for(Field field:tempClass.getDeclaredFields()) {
				//去重
				if(!fieldList.stream().anyMatch(a->a.getName().equals(field.getName()))) {
					currentList.add(field);
				}
			}
			fieldList.addAll(0, currentList);
		    tempClass = tempClass.getSuperclass(); //得到父类,然后赋给自己
		}
		return fieldList.stream()
				.filter(field->!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers()) && !field.isAnnotationPresent(ColumnIgnore.class))
				.collect(Collectors.toList());
	}

    /**
     * 利用反射设置指定对象的指定属性为指定的值
     *
     * @param obj        目标对象
     * @param fieldName  目标属性
     * @param fieldValue 目标值
     */
    public static void setFieldValue(Object obj, String fieldName, Object fieldValue) {
        Field field = ReflectUtil.getField(obj, fieldName);
        if (field != null) {
            try {
                field.setAccessible(true);
                field.set(obj, converter.convert(fieldValue,field.getType()));
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }


    /**
     * 根据文件路径 获取反射对象并执行对应方法
     * @param path 路径
     * @return 反射对象
     */
    public static Object reflectByPath(String path) {
        try {
            //获取类名
            String className = path.substring(0, path.lastIndexOf("."));
            //获取方法名
            String methodName = path.substring(path.lastIndexOf(".") + 1, path.length());
            // 获取字节码文件对象
            Class<?> c = Class.forName(className);
            Constructor<?> con = c.getConstructor();
            Object obj = con.newInstance();

            // public Method getMethod(String name,Class<?>... parameterTypes)
            // 第一个参数表示的方法名，第二个参数表示的是方法的参数的class类型
            Method method = c.getMethod(methodName);
            // 调用obj对象的 method 方法
            return method.invoke(obj);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static <T> String fnToFieldName(Converter<T> fn) {
        try {
            Method method = fn.getClass().getDeclaredMethod("writeReplace");
            method.setAccessible(Boolean.TRUE);
            SerializedLambda invoke = (SerializedLambda) method.invoke(fn);
            // 得到方法名
            String getter = invoke.getImplMethodName();
            // 切割得到字段名
            if(GET_PATTERN.matcher(getter).matches()) {
                getter =  getter.substring(3);
            }
            if (IS_PATTERN.matcher(getter).matches()) {
                getter = getter.substring(2);
            }
            return Introspector.decapitalize(getter);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException();
        }
    }
    
    public static <T,R> String fnToFieldName(SFunction<T,R> fn) {
        try {
            Method method = fn.getClass().getDeclaredMethod("writeReplace");
            method.setAccessible(Boolean.TRUE);
            SerializedLambda invoke = (SerializedLambda) method.invoke(fn);
//            System.out.println("invoke,"+invoke.toString());
//            System.out.println("getImplMethodName,"+invoke.getImplMethodName());
//            System.out.println("getImplClass,"+invoke.getImplClass());
//            System.out.println("getImplMethodSignature,"+invoke.getImplMethodSignature());
//            System.out.println("getInstantiatedMethodType,"+invoke.getInstantiatedMethodType());
//            System.out.println("getCapturedArg0,");
            // 得到方法名
            String getter = invoke.getImplMethodName();
            // 切割得到字段名
            if(GET_PATTERN.matcher(getter).matches()) {
                getter =  getter.substring(3);
            }
            if (IS_PATTERN.matcher(getter).matches()) {
                getter = getter.substring(2);
            }
            return Introspector.decapitalize(getter);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException();
        }
    }
    
    public static Class<?> getSuperClassGenricType(Class<?> clazz) {
        return getSuperClassGenricType(clazz, 0);
    }
    
    public static Class<?> getSuperClassGenricType(Class<?> clazz, int index)
            throws IndexOutOfBoundsException {
        Type genType = clazz.getGenericSuperclass();
        if (!(genType instanceof ParameterizedType)) {
            return Object.class;
        }
        Type[] params = ((ParameterizedType) genType).getActualTypeArguments();
        if (index >= params.length || index < 0) {
            return Object.class;
        }
        if (!(params[index] instanceof Class)) {
            return Object.class;
        }
        return (Class<?>) params[index];
    }
}