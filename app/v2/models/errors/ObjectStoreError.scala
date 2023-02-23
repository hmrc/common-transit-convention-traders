package v2.models.errors

sealed trait ObjectStoreError

object ObjectStoreError {
  case class UnexpectedError(thr: Option[Throwable] = None) extends ObjectStoreError
}
