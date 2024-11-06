Allure-Server Kubernetes Helm Chart
---

Helm chart to deploy Allure Server to Kubernetes.

### Download and install Helm

### Download chart directory

[allure-server chart](helm-allure)

### Execute commands
- `kubectl create namespace allure-server`  
  Create the namespace if it doesn’t exist:
- `helm dependency build`
- `helm delete allure-server -n allure-server`  
  Delete previous chart if exists in the namespace
- Go to root repository folder
- `helm upgrade --install allure-server ./helm-allure -n allure-server`  
  Install chart

### Execute commands

- Access via `ingress.host`
