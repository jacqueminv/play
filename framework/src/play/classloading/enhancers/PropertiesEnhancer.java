package play.classloading.enhancers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import javassist.CannotCompileException;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import play.classloading.ApplicationClasses.ApplicationClass;

public class PropertiesEnhancer extends Enhancer {

    @Override
    public void enhanceThisClass(ApplicationClass applicationClass) throws Exception {
        final CtClass ctClass = makeClass(applicationClass);

        for (CtField ctField : ctClass.getDeclaredFields()) {
            try {

                if (isProperty(ctField)) {

                    // Property name
                    String propertyName = ctField.getName().substring(0, 1).toUpperCase() + ctField.getName().substring(1);
                    String getter = "get" + propertyName;
                    String setter = "set" + propertyName;

                    try {
                        ctClass.getDeclaredMethod(getter);
                    } catch (NotFoundException noGetter) {

                        // Créé le getter
                        String code = "public " + ctField.getType().getName() + " " + getter + "() { return this." + ctField.getName() + "; }";
                        CtMethod getMethod = CtMethod.make(code, ctClass);
                        ctClass.addMethod(getMethod);
                    }

                    try {
                        ctClass.getDeclaredMethod(setter);
                    } catch (NotFoundException noSetter) {
                        // Créé le setter
                        CtMethod setMethod = CtMethod.make("public void " + setter + "(" + ctField.getType().getName() + " value) { this." + ctField.getName() + " = value; }", ctClass);
                        ctClass.addMethod(setMethod);
                    }
                    
                    ctField.setModifiers(Modifier.PRIVATE);

                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        // Intercepte les FieldAccess
        for (final CtBehavior ctMethod : ctClass.getDeclaredBehaviors()) {
            ctMethod.instrument(new ExprEditor() {

                @Override
                public void edit(FieldAccess fieldAccess) throws CannotCompileException {
                    try {

                        // Accés à une property ?
                        if (isProperty(fieldAccess.getField())) {

                            // TODO : vérifier que c'est bien un champ d'une classe de l'application (fieldAccess.getClassName())

                            // Si c'est un getter ou un setter
                            String propertyName = null;
                            if ((ctMethod.getName().startsWith("get") || ctMethod.getName().startsWith("set")) && ctMethod.getName().length() > 3) {
                                propertyName = ctMethod.getName().substring(3);
                                propertyName = propertyName.substring(0, 1).toLowerCase() + propertyName.substring(1);
                            }

                            // On n'intercepte pas le getter de sa propre property
                            if (propertyName == null || !propertyName.equals(fieldAccess.getFieldName())) {

                                String invocationPoint = ctClass.getName() + "." + ctMethod.getName() + ", ligne " + fieldAccess.getLineNumber();

                                if (fieldAccess.isReader()) {

                                    // Réécris l'accés en lecture à la property
                                    fieldAccess.replace("$_ = ($r)play.classloading.enhancers.PropertiesEnhancer.FieldAccessor.invokeReadProperty($0, \"" + fieldAccess.getFieldName() + "\", \"" + fieldAccess.getClassName() + "\", \"" + invocationPoint + "\");");

                                } else if (fieldAccess.isWriter()) {

                                    // Réécris l'accés en ecriture à la property
                                    fieldAccess.replace("play.classloading.enhancers.PropertiesEnhancer.FieldAccessor.invokeWriteProperty($0, \"" + fieldAccess.getFieldName() + "\", $type, $1, \"" + fieldAccess.getClassName() + "\", \"" + invocationPoint + "\");");

                                }
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        applicationClass.enhancedByteCode = ctClass.toBytecode();
        ctClass.defrost();
    }

    boolean isProperty(CtField ctField) {
        if (ctField.getName().equals(ctField.getName().toUpperCase()) || ctField.getName().substring(0, 1).equals(ctField.getName().substring(0, 1).toUpperCase())) {
            return false;
        }
        return Modifier.isPublic(ctField.getModifiers()) &&
                !Modifier.isFinal(ctField.getModifiers()) &&
                !Modifier.isStatic(ctField.getModifiers());
    }

    public static class FieldAccessor {

        public static Object invokeReadProperty(Object o, String property, String targetType, String invocationPoint) throws Throwable {
            if (o == null) {
                throw new NullPointerException("Lecture de la propriété " + property + " sur un objet null de type " + targetType + " (" + invocationPoint + ")");
            }
            String getter = "get" + property.substring(0, 1).toUpperCase() + property.substring(1);
            try {
                Method getterMethod = o.getClass().getMethod(getter);
                Object result = getterMethod.invoke(o);
                return result;
            } catch (NoSuchMethodException e) {
                return o.getClass().getField(property).get(o);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        public static void invokeWriteProperty(Object o, String property, Class valueType, boolean value, String targetType, String invocationPoint) throws Throwable {
            invokeWriteProperty(o, property, valueType, Boolean.valueOf(value), targetType, invocationPoint);
        }

        public static void invokeWriteProperty(Object o, String property, Class valueType, byte value, String targetType, String invocationPoint) throws Throwable {
            invokeWriteProperty(o, property, valueType, Byte.valueOf(value), targetType, invocationPoint);
        }

        public static void invokeWriteProperty(Object o, String property, Class valueType, char value, String targetType, String invocationPoint) throws Throwable {
            invokeWriteProperty(o, property, valueType, Character.valueOf(value), targetType, invocationPoint);
        }

        public static void invokeWriteProperty(Object o, String property, Class valueType, double value, String targetType, String invocationPoint) throws Throwable {
            invokeWriteProperty(o, property, valueType, Double.valueOf(value), targetType, invocationPoint);
        }

        public static void invokeWriteProperty(Object o, String property, Class valueType, float value, String targetType, String invocationPoint) throws Throwable {
            invokeWriteProperty(o, property, valueType, Float.valueOf(value), targetType, invocationPoint);
        }

        public static void invokeWriteProperty(Object o, String property, Class valueType, int value, String targetType, String invocationPoint) throws Throwable {
            invokeWriteProperty(o, property, valueType, Integer.valueOf(value), targetType, invocationPoint);
        }

        public static void invokeWriteProperty(Object o, String property, Class valueType, long value, String targetType, String invocationPoint) throws Throwable {
            invokeWriteProperty(o, property, valueType, Long.valueOf(value), targetType, invocationPoint);
        }

        public static void invokeWriteProperty(Object o, String property, Class valueType, short value, String targetType, String invocationPoint) throws Throwable {
            invokeWriteProperty(o, property, valueType, Short.valueOf(value), targetType, invocationPoint);
        }

        public static void invokeWriteProperty(Object o, String property, Class valueType, Object value, String targetType, String invocationPoint) throws Throwable {
            if (o == null) {
                throw new NullPointerException("Ecriture de la propriété " + property + " sur un objet null de type " + targetType + " (" + invocationPoint + ")");
            }
            String setter = "set" + property.substring(0, 1).toUpperCase() + property.substring(1);
            try {
                Method setterMethod = o.getClass().getMethod(setter, valueType);
                setterMethod.invoke(o, value);
            } catch (NoSuchMethodException e) {
                o.getClass().getField(property).set(o, value);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}