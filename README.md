# FaaS Demo

NOTE: this has only been tested in Linux.
Windows and Mac may be added at a later time.

## Summary

This is a demo to that shows deployment of plain-old Java functions (POJF) that are configured and deployed via a manifest.

The invoker bundle is composed of two containers:
1. envoy
2. service

Both are configured and built by the `fake-fake-pipeline` project.
Once the build has completed, the built Docker images are present in the local Docker instance.

The `fake-fake-pipeline` project also creates a `docker-compose.yml` file that can be used to launch the two images.
The `docker-compose.yml` can be thought of what goes into a K8s pod in reality. 
Both images are always run as a unit.

The output of the pipeline will show the command to use to launch the images.

TODO: both images talk to authz server that's not in the 'pod'

## Steps

First, build the projects:

```bash
$ mvn clean install
```

The install target will install the `faas-invoker` Docker image in the local Docker instance
so it can be used as base by the `fake-fake-pipeline` to build the actual service image.

Run the `deploy.sh` script, giving it a git-repo-like directory. 
The directory must have a `manifest.yml` file.

For the demo, use the provided `contact-functions-fake-repo` directory:

```bash
$ ./deploy.sh contact-functions-fake-repo
```

NOTE: The AuthZ server will have to be running. It is not launched by this process.
This process is to launch the 'pod' that implements the FaaS functions. The envoy
image (and also the service image, but that is optional) will communicate with
the AuthZ server.

