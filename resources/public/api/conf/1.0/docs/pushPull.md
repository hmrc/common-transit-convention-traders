You can use our Push-Pull-Notification Service (PPNS) to receive notifications of new messages from the NCTS as follows:

* if your endpoint is hosted by Amazon Web Services (AWS), you must use either [edge-optimised custom domain names](https://docs.aws.amazon.com/apigateway/latest/developerguide/how-to-edge-optimized-custom-domain-name.html) or [regional custom domain names](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-regional-api-custom-domain-create.html)
* you will receive a push notification each time when there is a new message for you to read
* for messages less than 100KB, a push notification will contain the message body
* a push notification will have a field containing the message URI
* you can use this URI to download the XML message from the CTC Traders API

Using this functionality means that you can avoid polling for new messages and thus save time and resources.