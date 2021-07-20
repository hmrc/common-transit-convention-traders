When writing code to use date filters in request URLs, you must always use percent-encoding. This is because some common characters used in dates and timestamps are not allowed to be used in URLs.

If you do not use percent-encoding you will get a 400 Bad Request as a default response.

For example:
 - the timestamp `2021-06-21T09:00+00:00` should be encoded as `2021-06-21T09%3A00%2B00%3A00`
 
When formatting query parameters into the request URL for date and time filtering functionality, you must only use the date and time format as specified in [Developer Hub](https://developer.service.hmrc.gov.uk/api-documentation/docs/reference-guide#common-data-types). You’ll then be using the common ISO 8601 standard `2021-06-21T09:00+00:00` that is compatible with our CTC Traders API.

Here are more examples in different programming languages:

Java
```
java.net.URLEncoder.encode("2021-04-30T16:08:31+00:00");
```

Python:
```
from urllib.parse import quote

quote('2021-04-30T16:08:31+00:00')
```

C#:
```
Uri.EscapeDataString("2021-04-30T16:08:31+00:00");
```

When sending requests to HMRC APIs you must always use percent-encoding within the URL to avoid getting any 400 Bad Requests.

You should also note:
 - some common data types described in the [Reference Guide](https://developer.service.hmrc.gov.uk/api-documentation/docs/reference-guide#common-data-types) on Developer Hub contain characters that are not valid for use in URLs
 - some software libraries do the percent-encoding for you automatically when you develop software using their facilities. Your web framework might also do this automatically for you

### Find out more
There are many frameworks and libraries that can handle percent-encoding for you. For background information about percent-encoding, we recommend you read (all links open in a new window):
 - [RFC](https://datatracker.ietf.org/doc/html/rfc3986)
 - [Wikipedia](https://en.wikipedia.org/wiki/Percent-encoding)
 - [MDN](https://developer.mozilla.org/en-US/docs/Glossary/percent-encoding)