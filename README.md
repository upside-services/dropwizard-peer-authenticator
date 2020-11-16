# dropwizard-peer-authenticator-aws-sm
Dropwizard module to enable BasicAuth security around a service with convenience factories for loading one or more maps of authorized users and their passwords from AWS Secrets Manager.

This artifact provides a Dropwizard Configuration/Factory class that enables convenient registration of an authorization filter with a Dropwizard Jersey Server.  This artifact is essentially a configuration wrapper around the documentation provided at https://www.dropwizard.io/en/latest/manual/auth.html#basic-authentication

A "Peer" object is a (username, password) POJO that models a remote service or user invoking some endpoint in your Dropwizard service.  The AllowedPeerAuthenticator loads a list of allowed peers from some source and then registers itself with Jersey to provide endpoint-level authentication on top of HTTP BasicAuth.  Because this is just a BasicAuth authenticator, your DW service should only be accessed over HTTPS.

## AWS Secrets Manager

Secrets Manager is a hosted service within AWS that allows you to store a map of secrets at a "coordinate" (aka "secret id").
Secrets can be isolated into different coordinates and selectively granted read access to different users based on the principle of least privilege. 
For example, assume your web app has a generic 'web' user that can do all PUT, POST, GET operations and also a special 'admin' user who is allowed to invoke DELETE endpoints.

You may isolate access to the 'web' user from access to the 'admin' user by storing those users in different secret coordinates with different IAM access policies for each secret:

* AWS Secrets Manager coordinate "services/prod/my_webapp/basic_auth/web" 
  holds secret `{ "web": "password!" }`
* AWS Secrets Manager coordinate "services/prod/my_webapp/basic_auth/admin"  
  holds secret `{ "admin": "supersecret!" }`
  
The IAM policy for the "web" user may restrict access to _just_ that coordinate:

```json
{
    "Version": "2012-10-17",
    "Statement": [
      {
          "Sid": "VisualEditor0",
          "Effect": "Allow",
          "Action": "secretsmanager:DescribeSecret",
          "Resource": "arn:aws:secretsmanager:us-east-1:1234567890:secret:services/prod/my_webapp/basic_auth/web"
      },
      {
          "Sid": "VisualEditor1",
          "Effect": "Allow",
          "Action": "secretsmanager:GetSecretValue",
          "Resource": "arn:aws:secretsmanager:us-east-1:1234567890:secret:services/prod/my_webapp/basic_auth/web"
      }
    ]
  }
```

Your Dropwizard service that allows either the `web` or the `admin` user to authenticate will need a policy that allows access to both coordinates, and this plugin provides the functionality to load multiple coordinates into a "soup" of authorized users with all (key, value) pairs from all enumerated coordinates combining together into the Authenticator.

## Integration Steps

### 1. Add a dependency 

Check [./RELEASE_NOTES.md](./RELEASE_NOTES.md) for the latest/best version for your needs, and add this to your pom:
```
<dependency>
    <groupId>com.getupside.dropwizard</groupId>
    <artifactId>dropwizard-peer-authenticator-aws-sm</artifactId>
    <version>2.0.0</version>
</dependency>
```

### 2. Update app config
In your app_config.yml, add a new stanza like:

```yaml
allowedPeers:
  secretCoordinates: ${AWS_SECRET_MANAGER_BASIC_AUTH}
```

In your application's Configuration class, add the AllowedPeerConfiguration object like:
```java

import io.dropwizard.Configuration;
import com.getupside.dw.auth.AllowedPeerConfiguration;

public class MyAppConfiguration extends Configuration {

    @JsonProperty("allowedPeers")
    private AllowedPeerConfiguration allowedPeers = new AllowedPeerConfiguration();

    public void setAllowedPeers(AllowedPeerConfiguration allowedPeers) {
        this.allowedPeers = allowedPeers;
    }

    public AwsConfiguration getAwsConfiguration() {
        return awsConfiguration;
    }
```

### 3. Register the Authenticator
In your main application class, register the authenticator with jersey:
```java 
public class MyApplication extends Application<MyConfiguration> {

    @Override
    public void run(MyConfiguration configuration, Environment environment) {
        configuration.getAllowedPeers().registerAuthenticator(environment);
```

### 4. Protect your Resources

Protect whatever resource endpoints you need with the Dropwizard @Auth annotation, like so:

```java
import io.dropwizard.auth.Auth;
import com.getupside.dw.auth.model.Peer;

@Path("/api/stuff")
@Produces(MediaType.APPLICATION_JSON)
public class MyResource {

    @GET
    public Response getStuff(@Auth Peer peer, ...) {
        return stuff;
    }
```

The @Auth annotation will be automatically hooked up to the AllowedPeerAuthenticator and will check every request to `getStuff()` to see if there's a BasicAuth header that authenticates against any of the (user, password) values loaded from the AWS Secrets Manager coordinates specified in the `$AWS_SECRET_MANAGER_BASIC_AUTH` environment variable.

### 5. Run your Service

When you run your Dropwizard service, be sure to run it with these environment variables:

```json
export AWS_SECRET_MANAGER_BASIC_AUTH="comma/separated/list, of/secrets/manager/coordinates, you/want/your/service/to/load"
export AWS_ACCESS_KEY_ID=< some IAM user/role with read access to the secret coordinate(s) >
export AWS_SECRET_ACCESS_KEY=< secret to go with the access key >
export AWS_REGION=< the region in which the secrets are stored >
```

_Note_: this plugin leverages the AWS [DefaultAWSCredentialsProviderChain](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html) and [DefaultAwsRegionProviderChain](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/regions/DefaultAwsRegionProviderChain.html) to source the IAM authorization and AWS Region values, so consult their documentation for precise search order if you experience issues with environment variables vs. system variables, etc.

## Test Support

Because you may not want everything that compiles your service to need full AWS Secrets Manager access, this authenticator supports classpath-accessible mocks.

For example, if your app_config.yml specifies:
```json
allowedPeers:
  secretCoordinates: ${AWS_SECRET_MANAGER_BASIC_AUTH:-"mock:/test-peers.json"}
```

Then by _not_ exporting a `AWS_SECRET_MANAGER_BASIC_AUTH` environment variable, the Authenticator will bootup and load the (key, value) pairs found at a classpath resource "test-peers.json" and will use all values found there as authenticated peers

Example "test-peers.json":
```json
{
  "mock_user": "some_secret",
  "another_user": "another_secret"
}
``` 