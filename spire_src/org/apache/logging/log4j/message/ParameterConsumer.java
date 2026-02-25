package org.apache.logging.log4j.message;

public interface ParameterConsumer<S> {
   void accept(Object parameter, int parameterIndex, S state);
}
