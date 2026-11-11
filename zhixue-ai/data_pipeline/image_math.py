#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""图像矩阵预处理：送 OCR 前的数学降噪与几何校正。

全部核心运算基于 NumPy 矩阵实现（cv2 仅作可选加速路径，缺失时自动降级）：

1. 灰度化      —— ITU-R BT.601 加权线性变换 Y = 0.299R + 0.587G + 0.114B
2. 高斯降噪    —— 可分离卷积：G(x,y) = g(x)·g(y)，g(k) ∝ exp(-k²/2σ²)，
                  水平一遍 + 垂直一遍，复杂度 O(HWk) 而非 O(HWk²)
3. Otsu 二值化 —— 最大类间方差 σ_B²(t) = ω₀(t)ω₁(t)[μ₀(t) - μ₁(t)]²，
                  256 级直方图上穷举，保证全局最优阈值
4. 倾斜校正    —— Postl 行投影方差法：θ* = argmax_θ Var(行投影和)，
                  用最近邻仿射映射旋转 θ*（扫描件小角度文本行对齐后行投影
                  波峰最尖锐、方差最大），再输出校正图
"""
from __future__ import annotations

import math

import numpy as np

try:  # 可选加速路径：有 cv2 时旋转/滤波走 SIMD 实现
    import cv2 as _cv2
except Exception:  # pragma: no cover - 环境分支
    _cv2 = None


def to_grayscale(image: np.ndarray) -> np.ndarray:
    """RGB(H,W,3) → 灰度(H,W)，float32/uint8 输入均可，输出 uint8。"""
    img = np.asarray(image)
    if img.ndim == 2:
        return img.astype(np.uint8)
    if img.ndim != 3 or img.shape[2] < 3:
        raise ValueError(f"期望 RGB 图像，得到 shape={img.shape}")
    if img.shape[2] > 3:  # RGBA → RGB
        img = img[:, :, :3]
    weights = np.array([0.299, 0.587, 0.114], dtype=np.float64)
    gray = img.astype(np.float64) @ weights
    return np.clip(np.rint(gray), 0, 255).astype(np.uint8)


def gaussian_kernel_1d(sigma: float, radius: int | None = None) -> np.ndarray:
    """归一化一维高斯核 g(k) = exp(-k²/2σ²)/Σexp(-j²/2σ²)，Σg = 1。"""
    if sigma <= 0:
        raise ValueError(f"sigma 必须 > 0，得到 {sigma}")
    if radius is None:
        radius = max(1, int(math.ceil(3.0 * sigma)))
    k = np.arange(-radius, radius + 1, dtype=np.float64)
    kernel = np.exp(-(k * k) / (2.0 * sigma * sigma))
    return kernel / kernel.sum()


def _convolve1d_rows(matrix: np.ndarray, kernel: np.ndarray) -> np.ndarray:
    """沿轴 1（水平）的 reflect 填充一维卷积，向量化实现。"""
    radius = kernel.size // 2
    padded = np.pad(matrix, ((0, 0), (radius, radius)), mode="reflect")
    windows = np.lib.stride_tricks.sliding_window_view(padded, kernel.size, axis=1)
    return windows @ kernel


def gaussian_blur(gray: np.ndarray, sigma: float = 1.0) -> np.ndarray:
    """可分离高斯模糊：水平 ⊛ g 后垂直 ⊛ g，复杂度 O(HWk)。"""
    kernel = gaussian_kernel_1d(sigma)
    data = gray.astype(np.float64)
    horizontal = _convolve1d_rows(data, kernel)
    vertical = _convolve1d_rows(horizontal.T, kernel).T
    return np.clip(np.rint(vertical), 0, 255).astype(np.uint8)


def otsu_threshold(gray: np.ndarray) -> int:
    """Otsu 全局最优二值化阈值：最大化类间方差（256 级直方图向量化实现）。

    推导：ω₀(t)=Σ_{i≤t}p_i，μ₀(t)=Σ_{i≤t} i·p_i/ω₀，μ_T=Σ i·p_i，
    σ_B²(t)=ω₀ω₁(μ₀-μ₁)² = [μ_T·ω₀(t) - μ(t)]² / [ω₀(t)(1-ω₀(t))]，
    其中 μ(t)=Σ_{i≤t} i·p_i 为累积一阶矩（用该式避免 μ₀ 分母为 0 的分支）。
    """
    if gray.size == 0:
        raise ValueError("空图像")
    hist = np.bincount(gray.ravel(), minlength=256).astype(np.float64)
    total = hist.sum()
    p = hist / total
    levels = np.arange(256, dtype=np.float64)
    omega = np.cumsum(p)                       # ω₀(t)
    mu = np.cumsum(p * levels)                 # μ(t)
    mu_t = mu[-1]
    denom = omega * (1.0 - omega)
    with np.errstate(divide="ignore", invalid="ignore"):
        sigma_b = np.where(denom > 0, (mu_t * omega - mu) ** 2 / denom, 0.0)
    sigma_b[0] = 0.0                           # t=0 时 ω₀=0 无意义
    return int(np.argmax(sigma_b))


def binarize(gray: np.ndarray) -> tuple[np.ndarray, int]:
    """Otsu 二值化：背景 0、前景 255（默认亮底暗字）。空图安全返回原样。"""
    if gray.size == 0:
        return gray.copy(), 0
    threshold = otsu_threshold(gray)
    binary = np.where(gray > threshold, 0, 255).astype(np.uint8)  # 暗字→255
    # 亮字暗底时前景占比会异常（>60%），翻转保证前景=文字
    if binary.mean() > 127 and (binary > 0).mean() > 0.6:
        binary = 255 - binary
    return binary, threshold


def _rotate_nearest(matrix: np.ndarray, angle_deg: float,
                    fill: int | None = None) -> np.ndarray:
    """最近邻仿射旋转（无 cv2 时的纯 NumPy 路径）。

    旋转矩阵 R(-θ) = [[cosθ, sinθ], [-sinθ, cosθ]]（图像坐标系 y 向下，
    逆时针校正即对采样坐标做顺时针旋转），输出画布扩大以容纳整图。
    fill 为越界填充值：None 取图像边框中位数（文档纸张色），
    避免黑色边框经二值化变成"前景"淹没行投影方差信号。
    """
    h, w = matrix.shape
    if fill is None:
        fill = int(np.median(np.concatenate([matrix[0, :], matrix[-1, :],
                                             matrix[:, 0], matrix[:, -1]])))
    theta = math.radians(angle_deg)
    cos, sin = math.cos(theta), math.sin(theta)
    # 原图四角旋转后的外接框
    corners = np.array([[0, 0], [w, 0], [0, h], [w, h]], dtype=np.float64)
    rotated = corners @ np.array([[cos, sin], [-sin, cos]]).T
    new_w = int(math.ceil(rotated[:, 0].max() - rotated[:, 0].min()))
    new_h = int(math.ceil(rotated[:, 1].max() - rotated[:, 1].min()))
    cx, cy = w / 2.0, h / 2.0
    ncx, ncy = new_w / 2.0, new_h / 2.0
    xs, ys = np.meshgrid(np.arange(new_w), np.arange(new_h))
    # 目标像素 → 源坐标（逆映射）
    dx, dy = xs - ncx, ys - ncy
    src_x = np.rint(cos * dx - sin * dy + cx).astype(np.int64)
    src_y = np.rint(sin * dx + cos * dy + cy).astype(np.int64)
    valid = (src_x >= 0) & (src_x < w) & (src_y >= 0) & (src_y < h)
    out = np.full((new_h, new_w), fill, dtype=matrix.dtype)
    out[ys[valid], xs[valid]] = matrix[src_y[valid], src_x[valid]]
    return out


def deskew_angle(binary: np.ndarray, max_angle: float = 10.0,
                 step: float = 0.5) -> float:
    """Postl 行投影方差法估计倾斜角：θ* = argmax Var(行和)。

    原理：文本行与水平轴对齐时，行投影曲线在文字行处尖锐、行间隙处平坦，
    投影方差最大；歪斜时行被涂抹、方差下降。仅在小角度（|θ|≤10°）内扫描，
    步长 0.5°——OCR 前置校正的常规精度。
    """
    if binary.size == 0:
        return 0.0
    # 缩小到固定高度再扫角度：方差比较与分辨率无关，图越小扫角度越快
    h, w = binary.shape
    target_h = 200
    if h > target_h:
        stride = max(1, int(round(h / target_h)))  # 行列同 stride 保持纵横比
        small = binary[::stride, ::stride]
        if small.size == 0:
            small = binary
    else:
        small = binary
    best_angle, best_var = 0.0, -1.0
    steps = int(round(max_angle / step))
    for i in range(-steps, steps + 1):
        angle = i * step
        rotated = _rotate_nearest(small, angle, fill=0)  # 二值图背景=0
        row_sums = rotated.sum(axis=1).astype(np.float64)
        variance = float(row_sums.var())
        if variance > best_var:
            best_var, best_angle = variance, angle
    return best_angle


def deskew(gray: np.ndarray, sigma: float = 1.0,
           max_angle: float = 10.0) -> tuple[np.ndarray, float]:
    """完整校正管线：高斯降噪 → Otsu 二值化 → 行投影方差估角 → 仿射旋转回正。

    返回 (校正后灰度图, 估计倾斜角)。cv2 可用时旋转用 cv2.warpAffine（双线性）。
    """
    denoised = gaussian_blur(gray, sigma)
    binary, _ = binarize(denoised)
    angle = deskew_angle(binary, max_angle=max_angle)
    if angle == 0.0:
        return gray.copy(), 0.0
    if _cv2 is not None:
        h, w = gray.shape
        matrix = _cv2.getRotationMatrix2D((w / 2.0, h / 2.0), angle, 1.0)
        return _cv2.warpAffine(gray, matrix, (w, h),
                               flags=_cv2.INTER_LINEAR,
                               borderMode=_cv2.BORDER_REPLICATE), angle
    return _rotate_nearest(gray, angle), angle
