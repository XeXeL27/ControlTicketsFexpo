package com.uap.control_tickets.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de errores. Convierte las excepciones que ocurren en
 * CUALQUIER controller en una respuesta JSON uniforme con el codigo HTTP
 * correcto. Asi el frontend siempre recibe el mismo formato de error.
 *
 * @RestControllerAdvice = intercepta las excepciones de todos los @RestController.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Formato estandar de respuesta de error
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String mensaje) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("mensaje", mensaje);
        return ResponseEntity.status(status).body(body);
    }

    // Recurso no encontrado -> 404
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleRecursoNoEncontrado(RecursoNoEncontradoException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Regla de negocio violada -> 400
    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<Map<String, Object>> handleNegocio(NegocioException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // @Valid en @RequestBody fallo -> 400 (devuelve mapa campo -> mensaje)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                errores.put(fe.getField(), fe.getDefaultMessage())
        );
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validacion fallida");
        body.put("campos", errores);
        return ResponseEntity.badRequest().body(body);
    }

    // @RequestParam obligatorio ausente -> 400
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleParamFaltante(MissingServletRequestParameterException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Parametro requerido ausente: " + ex.getParameterName());
    }

    // Violacion de constraint en parametros -> 400
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(ConstraintViolationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Credenciales incorrectas -> 401
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
    }

    // Usuario deshabilitado -> 401
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, Object>> handleDisabled(DisabledException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Usuario inactivo o eliminado");
    }

    // Sin permisos -> 403
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccesoDenegado(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta operacion");
    }

    // Cualquier otra excepcion no controlada -> 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenerico(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno del servidor. Contacte al administrador.");
    }
}
