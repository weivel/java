package io.github.intisy.ai.tsemit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Emit this member under the given name rather than its Java one.
 *
 * @implNote For a wire name Java cannot spell, such as a reserved word: a setting's {@code default}
 * is declared as {@code defaultValue()} and emitted as {@code default}.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface TsName {
    /** @return the name the member is emitted under */
    String value();
}
