package com.rental.pms.modules.user.exception;

import com.rental.pms.common.exception.BaseBusinessException;
import org.springframework.http.HttpStatus;

public class TokenExpiredException extends BaseBusinessException {

    public TokenExpiredException(String tokenType) {
        super(tokenType + " token has expired", "AUTH.TOKEN.EXPIRED", HttpStatus.UNAUTHORIZED);
    }
}
