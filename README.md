# Kong GitOps Experiment (DB-Less)

This repository contains a declarative GitOps configuration for managing a heterogeneous backend environment (Kubernetes Services + Serverless Cloud Run) using Kong DB-less mode.

## 架构说明 (Architecture)
此实验展示了如何在 Kubernetes 中彻底摒弃数据库 (PostgreSQL)，使用 KIC (Kong Ingress Controller) 实现严格隔离的双出口网关：

*   **`k8s-dp/` (Data Plane 1)**：专攻内部 K8s 生态。使用 `ingressClassName: kong-k8s`，代理集群内部的 2 个常规 Service。
*   **`cloudrun-dp/` (Data Plane 2)**：专攻外部 Serverless 生态。使用 `ingressClassName: kong-cloudrun`，代理 GCP 上的 2 个 Cloud Run Service。
    *   **核心特性**：借助 `ExternalName` Service 和 `request-transformer` 插件动态重写 `Host` 头，突破 Cloud Run 的安全校验。

## 部署方式 (Deployment)
将这两个目录直接通过 ArgoCD 等 GitOps 工具映射到目标 GKE 集群。
