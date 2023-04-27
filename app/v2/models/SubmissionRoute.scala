package v2.models

sealed trait SubmissionRoute

object SubmissionRoute {
  final case object ViaEIS  extends SubmissionRoute
  final case object ViaSDES extends SubmissionRoute
}
