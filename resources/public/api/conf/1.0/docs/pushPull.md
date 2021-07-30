You can use our Push Pull Notification Service to receive notifications of new messages from NCTS. 

* if your endpoint is hosted by Amazon Web Services (AWS) then you must either use [edge-optimised custom domain names](https://docs.aws.amazon.com/apigateway/latest/developerguide/how-to-edge-optimized-custom-domain-name.html) or [regional custom domain names](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-regional-api-custom-domain-create.html)
* this new functionality will send you a notification each time there is a new message for you to read
* for messages less than 100KB the Push Notification will contain the message body
* the Push Notification will have a field containing the messageURI
* you can then use this URI to download the XML message from the CTC Traders API

Using this functionality will save you time and resources by not having to poll for new messages.