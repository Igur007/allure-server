Allure-Server Kubernetes Helm Chart
---

Helm chart to deploy Allure Server to Kubernetes.

### Download and install Helm
- https://helm.sh/docs/intro/install/

### Download chart directory

[allure-server chart](.)

### Execute commands
- Create the namespace if it doesn’t exist:

    `kubectl create namespace allure-server`
  
- Build helm dependencies

    `helm dependency build`

- Delete previous chart if exists in the namespace

    `helm delete allure-server -n allure-server`  
  
- Go to root repository folder and install chart

    `helm upgrade --install allure-server ./helm-allure -n allure-server`  

- Access via `ingress.host` or port-forward allure-server service to localhost.
