package com.rental.pms.modules.user.exception;

import com.rental.pms.common.exception.BaseBusinessException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends BaseBusinessException {

    public InvalidCredentialsException() {
        super("Invalid email or password", "AUTH.CREDENTIALS.INVALID", HttpStatus.UNAUTHORIZED);
    }
}
