package com.github.pikosphere.di;

import javax.inject.Named;
import javax.inject.Qualifier;
import java.lang.annotation.Annotation;
import java.util.Objects;

public class ItemKey<T> {

    private static final String namedAnnotationFormat = "%s[%s]";
    private Class<? extends T> itemClass;
    private String annotation;


    public ItemKey(Class<? extends T> itemClass, String name) {
        assert itemClass != null : "itemClass cannot tbe null";
        this.itemClass = itemClass;
        if (name != null && !name.trim().isEmpty()) {
            this.annotation = String.format(namedAnnotationFormat, Named.class.getName(), name);
        }
    }

    public ItemKey(Class<? extends T> itemClass) {
        assert itemClass != null : "itemClass cannot tbe null";
        this.itemClass = itemClass;
    }


    public ItemKey(Class<? extends T> itemClass, Annotation qualifierAnnotation) {
        assert itemClass != null : "itemClass cannot tbe null";
        this.itemClass = itemClass;
        if (qualifierAnnotation != null) {
            assert qualifierAnnotation.annotationType().isAnnotationPresent(Qualifier.class) :
                    String.format("Annotation %s is not a valid annotation of type %s or one annotated with %s",
                            qualifierAnnotation, Named.class.getName(), Qualifier.class.getName());

            if (qualifierAnnotation instanceof Named) {
                Named tempNamedAnnotation = (Named) qualifierAnnotation;
                assert !(tempNamedAnnotation.value().trim().isEmpty()) :
                        String.format("Annotation %s cannot have an empty value!", qualifierAnnotation.annotationType());
                this.annotation = String.format(namedAnnotationFormat, Named.class.getName(), tempNamedAnnotation.value().trim());
            } else {
                this.annotation = qualifierAnnotation.annotationType().getName();
            }
        }
    }

    public ItemKey(Class<? extends T> itemClass, Class<? extends Annotation> annotationClass) {
        assert itemClass != null : "itemClass cannot tbe null";
        this.itemClass = itemClass;

        if (annotationClass != null) {
            assert annotationClass.isAnnotationPresent(Qualifier.class) :
                    String.format("Annotation class %s is not a valid annotation of type %s or one annotated with %s",
                            annotationClass.getName(), Named.class.getName(), Qualifier.class.getName());
            this.annotation = annotationClass.getName();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemKey)) return false;
        ItemKey<?> itemKey = (ItemKey<?>) o;

        boolean classesEqualCheck = itemClass.equals(itemKey.itemClass);

        boolean annotationEqualsCheck = false;

        if (annotation == null && itemKey.annotation == null) {
            annotationEqualsCheck = true;
        } else if (annotation != null && itemKey.annotation != null) {
            annotationEqualsCheck = annotation.equals(itemKey.annotation);
        }
        return classesEqualCheck &&
                annotationEqualsCheck;
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemClass, annotation);
    }

    @Override
    public String toString() {
        return "ItemKey{" +
                "itemClass=" + itemClass +
                ", annotation=" + annotation +
                '}';
    }
}
