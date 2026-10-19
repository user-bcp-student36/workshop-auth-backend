package com.bcp.workshop.auth;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class DeserializationMapperTest {
    @Test
    void doesNotRewriteUnrelatedHttpErrors() {
        var mapper = new LoginDeserializationExceptionMapper();
        try (var unauthorized = Response.status(Response.Status.UNAUTHORIZED).build();
             var badRequest = Response.status(Response.Status.BAD_REQUEST).build()) {
            assertSame(unauthorized, mapper.toResponse(new WebApplicationException(unauthorized)));
            assertSame(badRequest, mapper.toResponse(new WebApplicationException(badRequest)));
        }
    }
}
