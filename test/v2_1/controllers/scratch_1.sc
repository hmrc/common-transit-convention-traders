import models.common.MovementType.Departure
import v2_1.utils.CallOps.*

routing.routes.GenericRouting.getMovement(movementType = Departure, id = "123").urlWithContext