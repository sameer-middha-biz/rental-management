package com.rental.pms.modules.user.exception;

import com.rental.pms.common.exception.BaseBusinessException;
import org.springframework.http.HttpStatus;

public class UserDisabledException extends BaseBusinessException {

    public UserDisabledException() {
        super("User account is disabled", "USER.STATUS.DISABLED", HttpStatus.FORBIDDEN);
    }
}
