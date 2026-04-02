package com.rental.pms.modules.user.exception;

import com.rental.pms.common.exception.BaseBusinessException;
import org.springframework.http.HttpStatus;

public class InvitationExpiredException extends BaseBusinessException {

    public InvitationExpiredException() {
        super("Invitation has expired or is no longer valid", "USER.INVITATION.EXPIRED", HttpStatus.BAD_REQUEST);
    }
}
