package com.zhixue.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户（对齐 init-v2.sql 的 sys_user 表）。
 */
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long userId;

    /** 学号或工号，登录名 */
    private String username;

    /** BCrypt 哈希，禁止存明文 */
    private String password;

    private String realName;

    /** STUDENT / TEACHER */
    private String role;

    private String className;

    private Integer isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
