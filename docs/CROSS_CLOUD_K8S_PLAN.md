# 🌍 跨云分布式沙盒 (OCI CP1 + Tencent DP1) 实施演练指南

本文档是针对现代云原生架构的 **跨云 GitOps 网关沙盒** 实施路线图。旨在极限压榨免费云端资源的前提下，完成控制面与数据面跨越公网物理隔离的终极验证。

---

## 📍 阶段一：跨云基础设施准备 (双核双底座)

**目标**：拉起 OCI 的指挥部集群与 Tencent 的执行节点。

**1. 部署 OCI 控制面集群 (CP1)**:
*   **角色**：ArgoCD 指挥部
*   **节点**：`free-amd-vm2` (Server) + `free-amd-vm` (Worker)
*   **操作**：
    1. 给两台 1C1G 机器挂载 2GB Swap 防 OOM。
    2. 在 `vm2` 安装极其纯净的 K3s Server（禁用 Traefik, ServiceLB, Metrics-Server）。
    3. 在 `vm` 安装 K3s Agent 并加入 `vm2`，共同组建双节点轻量级控制平面。

**2. 部署 Tencent 数据面单机 (DP1)**:
*   **角色**：Kong 网关与 Quarkus 微服务运行地
*   **节点**：腾讯云 4G VM (`43.139.214.231`)
*   **操作**：安装单机版 K3s (同样需禁用默认 Traefik 以为 Kong 腾出 80/443 端口)，并提取公网可访问的 `kubeconfig` 文件。

---

## 🧠 阶段二：全栈控制面 (CP1) ArgoCD 部署与跨海接管

**目标**：在 OCI 上安装 GitOps 大脑，并跨公网接管腾讯云集群。

1. **在 OCI 安装极限阉割版 ArgoCD**:
   ```bash
   kubectl create namespace argocd
   kubectl apply --server-side -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
   ```
   *(注：为适应 OCI 的内存限制，需对 ArgoCD 资源进行裁剪，移除 Dex, Notifications 等非核心组件，并将耗内存的 `repo-server` 强制定向调度至 Worker 节点)*

2. **跨云接管 (将腾讯云加入 ArgoCD 麾下)**:
   获取腾讯云的 Kubeconfig 后，在 OCI 的终端通过 ArgoCD CLI 或直接配置 Secret，将腾讯云集群注册为受控端：
   ```bash
   argocd cluster add <tencent-context-name> --name tencent-dp1-cluster
   ```

---

## 🚀 阶段三：网关数据面 (DP1) 部署 (位于腾讯云)

**目标**：在腾讯云集群内部署负责翻译配置和转发流量的 Kong Ingress Controller (KIC)。

1. **添加 Kong Helm 仓库**:
   ```bash
   helm repo add kong https://charts.konghq.com
   helm repo update
   ```
2. **安装 DB-less Kong 节点 (绑定专属 ingress-class)**:
   ```bash
   helm install kong-dp1 kong/ingress \
     --namespace kong --create-namespace \
     --set ingressController.installCRDs=false \
     --set ingressController.ingressClass=kong-k8s \
     --set env.database="off"
   ```

---

## 🐍 阶段四：业务微服务构建 (Quarkus 极速版)

**目标**：在腾讯云构建一个基于 Quarkus 的现代轻量级 Java 服务，作为 Kong 代理的底层靶标。

1. **编写并编译 `GreetingResource.java` 示例**:
   ```java
   @Path("/svc1")
   public class GreetingResource {
       @GET
       @Produces(MediaType.APPLICATION_JSON)
       public Map<String, String> hello() {
           return Map.of("service", "Service-1", "message", "Hit successfully via Tencent Kong DP1!");
       }
   }
   ```
2. **本地镜像构建与直插 K3s 缓存**:
   ```bash
   ./mvnw clean package
   docker build -f src/main/docker/Dockerfile.jvm -t my-quarkus-svc:v1 .
   docker save my-quarkus-svc:v1 > svc1.tar
   k3s ctr images import svc1.tar
   ```

---

## 🔗 阶段五：闭环！跨云 GitOps 链路大贯通

**目标**：让 OCI 上的 ArgoCD 接管 GitHub 仓库，并通过公网自动在腾讯云拉起 Quarkus 业务 Pod，自动挂载 Kong 路由。

1. **在 GitHub 补充 K8s YAML**：
   在仓库的 `k8s-dp/business-apps/` 目录下推入 `Deployment` 和 `Service` 的 YAML。
   在 `k8s-dp/gateway-config/` 目录下推入 `Ingress` YAML（标注 `ingressClassName: kong-k8s`）。
2. **配置 ArgoCD App (跨集群 Target)**：
   在 OCI 的 ArgoCD UI 中新建 App：
   *   **Repository URL**: `https://github.com/nvd11/kong-gitops-experiment.git`
   *   **Path**: `k8s-dp`
   *   **Cluster URL**: 选择 **Tencent K3s 集群的 API URL** (不再是本地的 `https://kubernetes.default.svc`)
   *   **Namespace**: `default`
3. **见证跨云调度的时刻**：
   点击 `SYNC`。OCI 的机器拉取 GitHub 代码，生成部署指令并通过 `TCP 6443` 下发至腾讯云，腾讯云的微服务实例与 Kong 网关规则将瞬间生效！
4. **终极验证**：
   通过浏览器直接请求腾讯云的公网 IP `/svc1`，如果能成功返回 Quarkus 的 JSON，恭喜您，跨云控制的左半球（CP1+DP1）成功征服！
