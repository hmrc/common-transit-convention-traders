We use standard HTTP status codes to indicate whether an API request completed successfully.

The following list of standard error codes is subject to change.

<table>
    <tr>
        <th>Status code</td>
        <th>Description</td>
        <th>Explanation</td>
    </tr>
    <tr>
        <td>400</td>
        <td>Bad Request</td>
        <td>The <code>code</code> field in the JSON body provides the exact cause.  <code>SCHEMA_VALIDATION</code>: The request body failed to validate against the appropriate schema. Check the <code>validationErrors</code> field for more details</td>
    </tr>
    <tr>
        <td>401</td>
        <td>Unauthorized</td>
        <td>Invalid authentication credentials.</td>
    </tr>
    <tr>
        <td>403</td>
        <td>Forbidden</td>
        <td>Authentication token doesn't contain a valid Economic Operators Registration and Identification (EORI) number.</td>
    </tr>
    <tr>
        <td>404</td>
        <td>Not Found</td>
        <td>No object with specified ID found in the NCTS database, or a client passed an <code>Accept</code> header that contains the wrong API version number.</td>
    </tr>
    <tr>
        <td>413</td>
        <td>Request Entity Too Large</td>
        <td>A client request body is too large for the given endpoint.</td>
    </tr>
    <tr>
        <td>415</td>
        <td>Unsupported Media Type</td>
        <td>A client specified an invalid <code>Content-Type</code> header, or <code>Accept</code> header contains an invalid type.</td>
    </tr>
    <tr>
        <td>500</td>
        <td>Internal Server Error</td>
        <td>Code exception.</td>
    </tr>
    <tr>
        <td>501</td>
        <td>Not Implemented</td>
        <td>A user tried to <code>POST</code>  a message but the message type isn't currently supported.</td>
    </tr>
</table>

**Schema validation error responses**

If a request fails validation, the response body will contain a list of validation errors in the `validationErrors` field. Each error will contain the fields `lineNumber`, `columnNumber` and `message`. 

Below is an example response for a request that failed validation.

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
