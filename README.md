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

Run the `deploy.sh` script, ggiving it a git-repo-like directory. 
The directory must have a `manifest.yml` file.

For the demo, use the provided `contact-functions-fake-repo` directory:

```bash
$ ./deploy.sh contact-functions-fake-repo
```
