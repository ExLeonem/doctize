package jakarta.persistence;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) public @interface OneToOne { boolean optional() default true; }
