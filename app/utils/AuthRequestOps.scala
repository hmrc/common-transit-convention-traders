package utils

import controllers.actions.AuthRequest

object AuthRequestOps {

  implicit class AuthRequestOps(authRequest: AuthRequest[_]) {

    def hasNewEnrollment: Boolean = authRequest.hasNewEnrollment
  }

}
