Here is the list error codes that we will keep updating.
We use standard HTTP status codes to show whether an API request has succeeded or not: 

**POST**

400 BadRequest: XML has failed validation - see response body for details

401 Unauthorized: If client passes invalid auth credentials

403 Forbidden: If supplied auth token doesn't contain valid enrolment

404 Not Found: If no object with specified ID found in database

415 Unsupported Media Type: If the client specified an invalid ``Content-Type`` header

500 Internal Server Error: If exception in code occurs

501 Not Implemented: If user attempts to ``POST`` a message and the message type isn't currently supported

**PUT**

400 BadRequest: If XML message is invalid

401 Unauthorized: If client passes invalid auth credentials

403 Forbidden: If supplied auth token doesn't contain valid enrolment

404 Not Found: If no object with specified ID found in database

415 Unsupported Media Type: If the client specified an invalid ``Content-Type`` header

500 Internal Server Error: If exception in code occurs

**GET**

401 Unauthorized: If client passes invalid auth credentials

403 Forbidden: If supplied auth token doesn't contain valid enrolment

404 Not Found: If no object with specified ID found in database. Or if client passes in an ``Accept`` header which contains the wrong API version number. We have only released version 1.0 of the API

415 Unsupported Media Type: If ``Accept`` header contains invalid type

500 Internal Server Error: If exception in code occurs


Errors specific to each API are shown in the Endpoints section, under Response. 
See our [reference guide](https://developer.service.hmrc.gov.uk/api-documentation/docs/reference-guide#errors) for more on errors.
