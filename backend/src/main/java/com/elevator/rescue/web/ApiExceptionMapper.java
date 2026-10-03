package com.elevator.rescue.web;

import com.elevator.rescue.service.ApiException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {

    @Override
    public Response toResponse(ApiException ex) {
        Map<String, Object> body = Map.of(
                "error", ex.getMessage(),
                "details", ex.getDetails()
        );
        return Response.status(ex.getStatus())
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
