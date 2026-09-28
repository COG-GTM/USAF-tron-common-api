package mil.tron.commonapi.validations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Constraint(validatedBy = AppAndIdMatchValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface AppAndIdMatch {
    String message() default "App Source Id matches App Client Id";

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
