package jakarta.persistence;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) public @interface OneToMany { String mappedBy() default ""; }
