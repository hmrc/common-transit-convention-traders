When writing code to use date filters in request URLs, you must always use percent-encoding to avoid getting 400 Bad Request errors. This is because some common characters used in dates and timestamps cannot be used in URLs.

**Format**

When formatting query parameters into a request URL for date and time filtering functionality, you must use only the [ISO 8601](https://www.iso.org/iso-8601-date-and-time-format.html) standard for the date and time. For example, the timestamp `2021-06-21T09:00+00:00` should be encoded as `2021-06-21T09%3A00%2B00%3A00`. For more information about this, see our [Reference guide](/api-documentation/docs/reference-guide#common-data-types).

You should also note the following:

 - some common data types described in our [Reference guide](/api-documentation/docs/reference-guide#common-data-types) contain characters that are not valid for use in URLs
 - some software libraries and frameworks do percent-encoding for you automatically

**Examples**

Below are examples in different programming languages.

Java

```java
java.net.URLEncoder.encode("2021-04-30T16:08:31+00:00");
```

Python

```python
from urllib.parse import quote

quote('2021-04-30T16:08:31+00:00')
```

C#

```c#
Uri.EscapeDataString("2021-04-30T16:08:31+00:00");
```

**Find out more**

For background information about percent-encoding, we recommend the following:
 - [RFC](https://datatracker.ietf.org/doc/html/rfc3986)
 - [Wikipedia](https://en.wikipedia.org/wiki/Percent-encoding)
 - [MDN](https://developer.mozilla.org/en-US/docs/Glossary/percent-encoding)