```bash
#for testing
kc exec pod/sleep-557747455f-csqrw -c sleep -it -- /bin/sh
```


```bash
alias kc=kubectl
export EDITOR=nano

minikube tunnel -c &> /dev/null &

kc apply -f contact-functions.yaml

export INGRESS_IP=$(kubectl --namespace istio-system get service istio-ingressgateway -o=jsonpath="{.status.loadBalancer.ingress[0].ip}")
echo $INGRESS_IP

export HOST_HEADER=$(kc get ksvc contact-functions -o json | jq -r '.status.url | sub("https?://"; "")')
echo $HOST_HEADER

http -v ${INGRESS_IP} Host:$HOST_HEADER
http -v ${INGRESS_IP}/ping Host:$HOST_HEADER
http -v ${INGRESS_IP}/update Host:$HOST_HEADER id=123 name=kjell

kubectl edit configmap istio -n istio-system
```
```yaml
apiVersion: v1
data:
  mesh: |-
    # Add the following content to define the external authorizers.
    extensionProviders:
    - name: "grpc"
      envoyExtAuthzGrpc:
        service: "authz.default.svc.cluster.local"
        port: "8080"
    - name: "http"
      envoyExtAuthzHttp:
        service: "authz.default.svc.cluster.local"
        port: "8081"
        includeHeadersInCheck: ["x-ext-authz"]
    # You can keep the rest of the config as is
    defaultConfig:
      ...
```

```bash
kubectl rollout restart deployment/istiod -n istio-system

kc apply -f kram-authz.yaml


export AUTHZ_POD=$(kubectl get pod -l app=authz -n default -o jsonpath={.items..metadata.name})
echo $AUTHZ_POD

export SLEEP_POD=$(kubectl get pod -l app=sleep -n default -o jsonpath={.items..metadata.name})
echo $SLEEP_POD

export EXTERNAL_NAME=$(kc get service contact-functions -o=jsonpath="{.spec.externalName}")
echo $EXTERNAL_NAME

kc exec $SLEEP_POD -c sleep -- /usr/bin/curl -s "http://$EXTERNAL_NAME/" -H "Host: $HOST_HEADER"



kc logs $AUTHZ_POD

```




