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
    
### Package new helm chart:
- Update image version on values.yaml, e.g.:
  
    `image: "igur007/allure-server:v2.13.9"`
- Bump version in Chart.yaml, e.g.:

    `version: 0.1.4 ` 
- Package helm chart:
  
    `helm package ./helm-allure`
  

- Access via `ingress.host` or port-forward allure-server service to localhost.
