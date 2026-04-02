package com.rental.pms.modules.user.exception;

import com.rental.pms.common.exception.BaseBusinessException;
import org.springframework.http.HttpStatus;

public class TokenInvalidException extends BaseBusinessException {

    public TokenInvalidException(String tokenType) {
        super(tokenType + " token is invalid", "AUTH.TOKEN.INVALID", HttpStatus.UNAUTHORIZED);
    }
}
