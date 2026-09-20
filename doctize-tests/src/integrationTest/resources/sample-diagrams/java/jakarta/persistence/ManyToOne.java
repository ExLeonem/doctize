package jakarta.persistence;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) public @interface ManyToOne { boolean optional() default true; }
