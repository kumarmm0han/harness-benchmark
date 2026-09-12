package demo.sop;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

@RestControllerAdvice
public class Errors {
    public record Body(String code,String message,List<Compiler.Issue> issues) {}
    public static class Failure extends RuntimeException {
        final int status; final Body body;
        Failure(int status,String code,String message,List<Compiler.Issue> issues){super(message);this.status=status;this.body=new Body(code,message,issues);}
    }
    public static Failure fail(int status,String code,String message){return new Failure(status,code,message,List.of());}
    @ExceptionHandler(Failure.class) ResponseEntity<Body> failure(Failure e){return ResponseEntity.status(e.status).body(e.body);}
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,HttpRequestMethodNotSupportedException.class,org.springframework.web.HttpMediaTypeNotSupportedException.class}) ResponseEntity<Body> malformed(Exception e){return failure(fail(400,"BAD_REQUEST","Malformed request."));}
    @ExceptionHandler(NoResourceFoundException.class) ResponseEntity<Body> missing(Exception e){return failure(fail(404,"NOT_FOUND","Resource not found."));}
    @ExceptionHandler(Exception.class) ResponseEntity<Body> unexpected(Exception e){return failure(fail(500,"INTERNAL_ERROR","The request could not be completed."));}
}
