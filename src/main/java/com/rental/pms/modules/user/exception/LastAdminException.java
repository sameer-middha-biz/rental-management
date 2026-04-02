package com.rental.pms.modules.user.exception;

import com.rental.pms.common.exception.BaseBusinessException;
import org.springframework.http.HttpStatus;

public class LastAdminException extends BaseBusinessException {

    public LastAdminException() {
        super("Cannot remove the last agency admin", "USER.ADMIN.LAST_REMAINING", HttpStatus.CONFLICT);
    }
}
