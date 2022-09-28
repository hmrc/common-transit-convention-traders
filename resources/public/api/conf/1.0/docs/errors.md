We use standard HTTP status codes to indicate whether an API request completed successfully.

The following list of standard error codes is subject to change.

| Status code | Description | Explanation |
| ----------- | ----------- | ----------- |
| 400 | Bad Request | XML has failed validation (see response body for details). |
| 401 | Unauthorized | Invalid authentication credentials. |
| 403 | Forbidden | Authentication token doesn't contain a valid Economic Operators Registration and Identification (EORI) number. |
| 404 | Not Found | No object with specified ID found in the NCTS database, or a client passed an `Accept` header that contains the wrong API version number. |
| 415 | Unsupported Media Type | A client specified an invalid `Content-Type` header, or `Accept` header contains an invalid type. |
| 500 | Internal Server Error | Code exception. |
| 501 | Not Implemented | A user tried to `POST` a message but the message type isn't currently supported. |

Some API endpoints have additional errors - see the Error scenarios of the relevant endpoints.

For more information about API errors, see our [Reference guide](/api-documentation/docs/reference-guide#errors).
