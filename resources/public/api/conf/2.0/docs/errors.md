Here is the list of error codes that we will keep updating.
We use standard HTTP status codes to show whether an API request has succeeded or not:

**POST**

400 BadRequest: The "code" field in the Json body provides the exact cause:

* `SCHEMA_VALIDATION`: The request body failed to validate against the appropriate schema. Check the `validationErrors` field for more details.

401 Unauthorized: If client passes invalid auth credentials

403 Forbidden: If supplied auth token doesn't contain valid enrolment

404 Not Found: If no object with specified ID found in database

413 Request Entity Too Large: If the client request body is too large for the given endpoint

415 Unsupported Media Type: If the client specified an invalid ``Content-Type`` header

500 Internal Server Error: If exception in code occurs

501 Not Implemented: If user attempts to ``POST`` a message and the message type isn't currently supported

**PUT**

400 BadRequest: The "code" field in the Json body provides the exact cause:

* `SCHEMA_VALIDATION`: The request body failed to validate against the appropriate schema. Check the `validationErrors` field for more details.

401 Unauthorized: If client passes invalid auth credentials

403 Forbidden: If supplied auth token doesn't contain valid enrolment

404 Not Found: If no object with specified ID found in database

413 Request Entity Too Large: If the client request body is too large for the given endpoint

415 Unsupported Media Type: If the client specified an invalid ``Content-Type`` header

500 Internal Server Error: If exception in code occurs

**GET**

401 Unauthorized: If client passes invalid auth credentials

403 Forbidden: If supplied auth token doesn't contain valid enrolment

404 Not Found: If no object with specified ID found in database. Or if client passes in an ``Accept`` header which contains the wrong API version number.

415 Unsupported Media Type: If ``Accept`` header contains invalid type

500 Internal Server Error: If exception in code occurs


Errors specific to each API are shown in the Endpoints section, under Response.
See our [reference guide](https://developer.service.hmrc.gov.uk/api-documentation/docs/reference-guide#errors) for more on errors.

### Schema Validation Error Responses

If a request fails to pass validation, the response body will contain a list of validation errors in the "validationErrors" field. Each error will contain the fields "lineNumber", "columnNumber" and "message". 

An example response for a request that failed to validate can be found below:

```json
{
  "message": "Request failed schema validation",
  "code": "SCHEMA_VALIDATION",
  "validationErrors": [
    {
      "lineNumber": 4,
      "columnNumber": 60,
      "message": "cvc-pattern-valid: Value 'notatime' is not facet-valid with respect to pattern '\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}' for type 'PreparationDateAndTimeContentType'."
    },
    {
      "lineNumber": 4,
      "columnNumber": 60,
      "message": "cvc-type.3.1.3: The value 'notatime' of element 'preparationDateAndTime' is not valid."
    }
  ]
}
```
