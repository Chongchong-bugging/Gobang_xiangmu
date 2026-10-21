package com.gobang.security;

import java.io.Serial;
import java.io.Serializable;

/** Minimal authenticated identity stored in the HTTP session. */
public record AuthenticatedUser(Long id, String nickname) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
