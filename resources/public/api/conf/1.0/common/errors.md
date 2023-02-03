We use standard HTTP status codes to indicate whether an API request completed successfully.

The following list of standard error codes is subject to change.

<table>
	<tr>
		<th>Status code</th>
		<th>Description</th>
		<th>Explanation</th>
 	</tr>
 	<tr>
  		<td>400</td>
   		<td>Bad Request</td>
			<td>Invalid XML message (see response body for details).</td>
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
			<td>A user tried to <code>POST</code> a message but the message type isn't currently supported.</td>
 	</tr>
</table>

Some API endpoints have additional errors - see the Error scenarios of the relevant endpoints.

For more information about API errors, see our [Reference guide](/api-documentation/docs/reference-guide#errors).
