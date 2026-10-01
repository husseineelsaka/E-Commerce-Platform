# ArgoCD

`applications.yaml` declares one ArgoCD `Application` per service. Each points at `deployment/helm/<app>` on `main` of this repository and syncs automatically with `prune: true` and `selfHeal: true`: a change merged to `main` is deployed, a deleted resource is pruned, and a manual change in the cluster is reverted (drift detection).

Install ArgoCD (pinned release) into the kind cluster from `deployment/kubernetes/up.sh`, then apply the Applications:

```sh
kubectl create namespace argocd
kubectl apply -n argocd --server-side -f https://raw.githubusercontent.com/argoproj/argo-cd/v3.5.3/manifests/install.yaml
kubectl apply -f deployment/argocd/applications.yaml
kubectl -n argocd get applications
```

The repository is public, so ArgoCD needs no repository credentials. The Applications deploy the locally built images that `up.sh` loads into kind (`ecommerce/<app>:local`).

UI: `kubectl -n argocd port-forward svc/argocd-server 8443:443`, then https://localhost:8443 with user `admin` and the password from `kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}"` (base64).
