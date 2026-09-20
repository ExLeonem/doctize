package jakarta.persistence;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) public @interface Table { String name() default ""; }
