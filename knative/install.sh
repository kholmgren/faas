export EDITOR=nano
alias kc=kubectl

minikube start

curl -L https://istio.io/downloadIstio | sh -
alias istioctl='./istio-1.9/bin/istioctl
'
./istioctl version

istioctl install --set profile=default

kc apply --filename https://github.com/knative/serving/releases/download/v0.21.0/serving-crds.yaml
kc apply --filename https://github.com/knative/serving/releases/download/v0.21.0/serving-core.yaml
kc apply --filename https://github.com/knative/serving/releases/download/v0.21.0/serving-core.yaml

kc label ns default istio-injection=enabled

#minikube tunnel --cleanup

