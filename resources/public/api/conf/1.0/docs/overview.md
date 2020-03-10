This API allows you to send arrival movement notifications to the New Computerised Transit System. You can also retrieve messages sent from the Office of Departure and Office of Destination.

Updates to end point documentation 

## Pull all arrival notifications 
Pull all arrival messages sent to the Office of Destination in the last 28 days.

Send an arrivals notification Send a message to let the Office of Destination know that the goods have arrived. This message will be sent when the goods reach their final destination. It is also called an E_ARR_NOT (IE007).

## Pull all notifications for a transaction ID
Pull a specific message which was sent to the Office of Destination in the last 28 days.

## Pull all messages relating to an arrival movement
Pull all messages sent in the last 28 days relating to an arrival movement. You will only be able to retrieve these messages if you have the EORI associated with the arrival movement.  

## Pull a message relating to an arrival movement or message ID
Pull all messages relating to a specific arrival movement or message ID. For example, this could include messages about route diversions or unloading permissions. Any messages more than 28 days old will be archived. Archived messages will be return a “not found” message.

Further details of the User Restricted Authentication are given on the 
[Authorisation](/api-documentation/docs/authorisation) page.

For more information about how to develop your own client applications, including example clients for this API, 
see [Tutorials](/api-documentation/docs/tutorials).