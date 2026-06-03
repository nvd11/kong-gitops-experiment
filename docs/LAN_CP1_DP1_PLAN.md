# 🏰 局域网本地堡垒 (CP1+DP1) 实施演练指南

本文档是针对本地局域网（Site 1）环境的 **GitOps 网关沙盒** 实施路线图。旨在零云端成本的前提下，利用单台 VM 完成完整架构的验证。

---

## 📍 阶段一：极简基础设施准备 (K3s 底座)

**目标**：拉起一个轻量级、低消耗的 Kubernetes 运行环境。
**资源门槛**：一台 2核 4G+（推荐 8G）的 Ubuntu/Debian 虚拟机。

1. **一键安装 K3s (包含 Master+Worker)**:
   ```bash
   curl -sfL https://get.k3s.io | sh -
   ```
2. **配置 kubectl 环境变量** (让本地命令直接接管集群):
   ```bash
   export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
   # 验证集群存活
   kubectl get nodes
   ```

---

## 🧠 阶段二：全栈控制面 (CP1) ArgoCD 部署

**目标**：安装 GitOps 的核心大脑。

1. **创建命名空间并安装 ArgoCD**:
   ```bash
   kubectl create namespace argocd
   kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
   ```
2. **暴露 Web UI (供本地浏览器访问)**:
   ```bash
   kubectl patch svc argocd-server -n argocd -p '{"spec": {"type": "NodePort"}}'
   # 或者使用端口转发临时访问：kubectl port-forward svc/argocd-server -n argocd 8080:443
   ```
3. **获取初始管理员 (admin) 密码**:
   ```bash
   kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d; echo
   ```

---

## 🚀 阶段三：网关数据面 (DP1) 部署

**目标**：在集群内部署负责翻译配置和转发流量的 Kong Ingress Controller (KIC)。

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
   *(注：在真实的 GitOps 中，Kong 自身的 Helm Chart 也可以交由 ArgoCD 统管，但首次为了简化鸡生蛋问题，可手动 Helm 安装基础设施)*

---

## 🐍 阶段四：业务微服务构建 (Quarkus 极速版)

**目标**：构建一个基于 Quarkus 现代轻量级框架的 Java 接口服务，作为 Kong 代理的底层靶标。

我们将提供一份标准代码骨架。您可以将其打成 Docker 镜像：
**`GreetingResource.java` 示例**:
```java
package org.acme;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

@Path("/svc1")
public class GreetingResource {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> hello() {
        return Map.of("service", "Service-1", "message", "Hit successfully via Kong DP1!");
    }
}
```
**编译与打包 (在 Quarkus 项目下)**:
```bash
./mvnw clean package
docker build -f src/main/docker/Dockerfile.jvm -t my-quarkus-svc:v1 .
```
*(为了极致的本地开发速度，您可以直接使用 `k3s ctr images import` 将本地打好的镜像直接喂给 K3s 缓存，省去上传 DockerHub 的烦恼！)*

---

## 🔗 阶段五：闭环！GitOps 链路大贯通

**目标**：让 ArgoCD 接管 GitHub 仓库，自动拉起 Quarkus 业务 Pod，并自动挂载 Kong 路由。

1. **在 GitHub 补充 K8s YAML**：
   在仓库的 `k8s-site/business-apps/` 目录下推入 `Deployment` 和 `Service` 的 YAML。
   在 `k8s-site/gateway-config/` 目录下推入 `Ingress` YAML（标注 `ingressClassName: kong-k8s`）。
2. **配置 ArgoCD App**：
   在 ArgoCD UI 中点击 `+ NEW APP`。
   *   **Repository URL**: `https://github.com/nvd11/kong-gitops-experiment.git`
   *   **Path**: `k8s-site`
   *   **Cluster URL**: `https://kubernetes.default.svc`
   *   **Namespace**: `default`
3. **见证奇迹的时刻**：点击 `SYNC`，所有 K8s 节点（微服务实例 + Kong 网关规则）将瞬间全部变绿！
4. **终极验证**：用浏览器请求本地 Kong 网关的 IP `/svc1`，如果能成功返回 FastAPI 的 JSON，恭喜您，左半球（CP1+DP1）成功征服！
