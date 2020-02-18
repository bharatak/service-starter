package com.thoughtworks.handlers;

import com.thoughtworks.exceptions.BusinessException;
import com.thoughtworks.exceptions.ResourceConflictException;
import com.thoughtworks.exceptions.ResourceNotFoundException;
import com.thoughtworks.exceptions.ValidationException;
import com.thoughtworks.logger.ErrorEvent;
import com.thoughtworks.api.PaymentFailureResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class ExceptionMessageHandler {
    private static Logger logger = LogManager.getLogger(ExceptionMessageHandler.class);

    void logException(Exception ex){
        new ErrorEvent(ex.getClass().toString(), ex.getMessage(), logger)
                .addProperty("stackTrace", Arrays.toString(ex.getStackTrace())).publish();
        Throwable causedByException = ex.getCause();
        if((causedByException)!=null) {
            new ErrorEvent(causedByException.getClass().toString(), causedByException.getMessage(), logger)
                    .addProperty("stackTrace", Arrays.toString(causedByException.getStackTrace())).publish();
        }
    }


    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    protected PaymentFailureResponse handleResourceNotFoundException(ResourceNotFoundException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put(ex.getKey(), ex.getValue());
        logException(ex);
        return new PaymentFailureResponse("MISSING_INFO", errors);
    }

    @ExceptionHandler(ResourceConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    protected PaymentFailureResponse handleConflictException(ResourceConflictException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put(ex.getKey(), ex.getValue());
        logException(ex);
        return new PaymentFailureResponse("REQUEST_CONFLICT", errors);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    @ResponseBody
    protected String handleCircuitBreakException(CallNotPermittedException callNotPermittedException) {
        logException(callNotPermittedException);
        return "CircuitBreakerException";
    }

    @ExceptionHandler({MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public PaymentFailureResponse handleArgumentValidationException(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();

        List<Map.Entry<String, String>> fieldErrorMessages = exception.getBindingResult().getFieldErrors().stream()
                .map(ExceptionMessageHandler::extractMessage)
                .collect(Collectors.toList());

        for (Map.Entry<String, String> errorMessage : fieldErrorMessages) {
            if (errors.containsKey(errorMessage.getKey())) {
                String updatedMsg = errors.get(errorMessage.getKey()) + "; " + errorMessage.getValue();
                errors.put(errorMessage.getKey(), updatedMsg);
            } else {
                errors.put(errorMessage.getKey(), errorMessage.getValue());
            }
        }

        logException(exception);
        return new PaymentFailureResponse("INVALID_INPUT", errors);
    }
    private static Map.Entry<String, String> extractMessage(FieldError fieldError) {
        return new HashMap.SimpleEntry<>(fieldError.getField(), fieldError.getDefaultMessage());
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    protected PaymentFailureResponse handleValidationException(ValidationException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put(ex.getKey(), ex.getValue());
        logException(ex);
        return new PaymentFailureResponse("INVALID_INPUT", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    protected PaymentFailureResponse handleRequestNotReadableException(HttpMessageNotReadableException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("message", "Request body missing or incorrect format");
        logException(ex);
        return new PaymentFailureResponse("INVALID_INPUT", errors);
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    @ResponseBody
    protected PaymentFailureResponse handleProcessingException(BusinessException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put(ex.getKey(), ex.getValue());
        logException(ex);
        return new PaymentFailureResponse("REQUEST_UNPROCESSABLE", errors);
    }



    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    protected PaymentFailureResponse handleGeneralException(Exception ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("message", "Could not process the request");
        logException(ex);
        return new PaymentFailureResponse("SERVER_ERROR", errors);
    }

}
