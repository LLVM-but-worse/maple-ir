package org.mapleir.ir.antlr.source;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.mapleir.ir.antlr.error.CompilationException;
import org.mapleir.ir.antlr.error.ErrorReporter;

/**
 * Annotation that indicates that a method can throw {@link CompilationException}s and
 * thus requires an active {@link SourcePosition} to base these errors on.
 * <p>
 * <b>For callers: </b><br>
 * Essentially, before calling a method that is marked with this annotation,
 * care must be taken to ensure that the input that the marked method works on
 * has a corresponding source position or base that is pushed onto the active
 * source position stack.
 * <p>
 * <b>For callees: </b><br>
 * Methods that mark themselves with this annotation should only work on a
 * single input that was derived from the active lexed token. When throwing an
 * error, a charOffset should be given to the {@link ErrorReporter} or error
 * handling mechanism which is the offset from the start of the active source
 * position to the character/digit/element that spawned the error.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = { ElementType.METHOD })
public @interface ExpectsSourcePosition {
}