package dk.openesdh.addo.exception;


import org.springframework.aop.ThrowsAdvice;

public class ExceptionWrapper implements ThrowsAdvice {

    public void afterThrowing(Throwable e) throws Throwable {
        throw new AddoException(e);
    }

}
